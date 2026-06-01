package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import java.util.Random
import kotlin.math.exp
import kotlin.math.sin

/**
 * 掉血爱心粒子：生物掉血时，在血条所在世界位置生成会下落、晃动、渐隐的爱心。
 *
 * 粒子生成后与实体解耦（记录绝对世界坐标，仅受自身物理影响），模仿原版掉血爱心的表现。
 *
 * 与原版粒子一致：物理按**客户端 tick**（20Hz）定步推进，每颗粒子记录上一 tick 位置，
 * 渲染时用 `partialTick` 在前后两 tick 间插值，从而既丝滑又随暂停/`/tick freeze` 自然停摆。
 *
 * 「弹簧」手感在渲染期施加：横向摆动(sway)+倾斜(tilt) 为**阻尼谐振**（振幅随时间指数衰减），
 * 重档再叠一层高频抖动(jitter)。幅度按本次伤害分档（见 [styleFor]），越重越震撼。
 */
object HeartParticleManager {
    // 物理量按“每 tick”语义取值（1 tick = 1/20 s）。
    private const val GRAVITY = 0.0045 // 每 tick 对竖直速度的衰减（已放缓，掉落更轻盈）
    private const val MAX_AGE_TICKS = 28 // 约 1.4s
    private const val FADE_TICKS = 5 // 末尾 0.25s 才开始淡出（其余时间不透明，契合像素观感）
    private const val SCALE = 0.025f
    private const val MAX_PARTICLES = 1024

    // 渲染时沿“相机→粒子”视线把粒子朝相机方向挪动的距离比例：
    // billboard 沿视线平移不改变屏幕位置，仅令其深度严格更靠前，从而覆盖（写深度的）血条爱心，避免共面被遮挡。
    private const val FRONT_FRACTION = 0.05

    // 阻尼谐振（“弹簧”）参数。
    private const val SWAY_OMEGA = 0.85f // 主摆动角频率（rad/tick，已放缓）
    private const val SWAY_DAMP = 0.10f // 每 tick 振幅衰减系数
    private const val JITTER_OMEGA = 2.6f // 重档高频抖动角频率
    private const val DEG_TO_RAD = 0.017453292f

    /**
     * 粒子掉落/晃动风格，按伤害分档。
     * @param springScale 弹起初速倍率（越大弹得越高）。
     * @param swayAmp 横向摆动幅度（占爱心宽度比例）。
     * @param tiltDeg 倾斜摆动幅度（度）。
     * @param jitterAmp 高频抖动幅度（占爱心宽度比例，仅重档 > 0）。
     */
    class ParticleStyle(
        val springScale: Float,
        val swayAmp: Float,
        val tiltDeg: Float,
        val jitterAmp: Float,
    )

    /**
     * 按本次总伤害选择掉落风格：
     * - 轻档（< medium）：轻微晃动；
     * - 中档（[medium, heavy]）：中等幅度晃动；
     * - 重档（> heavy）：加大弹簧 + 叠加高频抖动，体感震撼。
     * @param shakeScale 全局抖动幅度倍率（仅缩放 sway/tilt/jitter，不改变弹起高度）。
     */
    fun styleFor(damage: Float, medium: Float, heavy: Float, shakeScale: Float): ParticleStyle {
        val s = shakeScale.coerceAtLeast(0.0f)
        return when {
            damage > heavy -> ParticleStyle(1.7f, 0.46f * s, 28.0f * s, 0.14f * s)
            damage >= medium -> ParticleStyle(1.3f, 0.30f * s, 17.0f * s, 0.0f)
            else -> ParticleStyle(1.0f, 0.16f * s, 9.0f * s, 0.0f)
        }
    }

    private class Particle(
        var x: Double,
        var y: Double,
        var z: Double,
        var vx: Double,
        var vy: Double,
        var vz: Double,
        val texture: Identifier,
        val flipU: Boolean,
        val style: ParticleStyle,
        val phase: Float,
    ) {
        // 上一 tick 的位置，供渲染插值；生成时与当前位置相同，避免首帧跳变。
        var xo = x
        var yo = y
        var zo = z
        var age = 0
        var ageo = 0
    }

    private val particles = ArrayList<Particle>()
    private val random = Random()

    /**
     * 在指定世界坐标生成一颗掉落爱心粒子，贴图（含颜色）由调用方按对应爱心给定。
     * 初始位置即给定坐标（不再随机散开），确保与血条上那颗爱心像素级重合。
     * @param flipU 半心翻转填充侧，与血条上的半心保持一致（满心对称，传入无影响）。
     */
    fun spawn(x: Double, y: Double, z: Double, texture: Identifier, flipU: Boolean, style: ParticleStyle) {
        if (particles.size >= MAX_PARTICLES) return
        particles.add(
            Particle(
                x,
                y,
                z,
                (random.nextDouble() - 0.5) * 0.012,
                (0.010 + random.nextDouble() * 0.008) * style.springScale,
                (random.nextDouble() - 0.5) * 0.012,
                texture,
                flipU,
                style,
                (random.nextDouble() * Math.PI * 2.0).toFloat(),
            ),
        )
    }

    /** 每客户端 tick 定步推进所有粒子物理，移除过期粒子。 */
    fun tick() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.xo = p.x
            p.yo = p.y
            p.zo = p.z
            p.ageo = p.age
            p.vy -= GRAVITY
            p.x += p.vx
            p.y += p.vy
            p.z += p.vz
            p.age++
            if (p.age >= MAX_AGE_TICKS) iterator.remove()
        }
    }

    fun isEmpty(): Boolean = particles.isEmpty()

    fun render(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        cameraPosition: Vec3,
        cameraOrientation: Quaternionf,
        partialTick: Float,
    ) {
        val halfSize = HeartGraphics.SIZE / 2.0f
        for (p in particles) {
            // 前后两 tick 位置插值，得到当前帧的平滑位置与年龄。
            val px = p.xo + (p.x - p.xo) * partialTick
            val py = p.yo + (p.y - p.yo) * partialTick
            val pz = p.zo + (p.z - p.zo) * partialTick
            val ageRender = p.ageo + (p.age - p.ageo) * partialTick

            // 仅在末尾 FADE_TICKS 内淡出，其余时间完全不透明。
            val fadeStart = MAX_AGE_TICKS - FADE_TICKS
            val fadeFactor = if (ageRender <= fadeStart) {
                1.0f
            } else {
                ((MAX_AGE_TICKS - ageRender) / FADE_TICKS).coerceIn(0.0f, 1.0f)
            }
            val alpha = (255.0f * fadeFactor).toInt().coerceIn(0, 255)
            val color = (alpha shl 24) or 0xFFFFFF

            // 阻尼谐振（“弹簧”）：振幅随年龄指数衰减，起始最强（撞击感）后逐渐平复。
            val style = p.style
            val damp = exp(-SWAY_DAMP * ageRender)
            val swayLocal = (style.swayAmp * sin(SWAY_OMEGA * ageRender + p.phase) +
                style.jitterAmp * sin(JITTER_OMEGA * ageRender + p.phase * 1.7f)) * damp * HeartGraphics.SIZE
            val tiltRad = style.tiltDeg * sin(SWAY_OMEGA * ageRender + p.phase) * damp * DEG_TO_RAD

            val texture = p.texture
            poseStack.pushPose()
            try {
                // 朝相机方向沿视线挪动 FRONT_FRACTION：缩放“相机→粒子”偏移向量即可（屏幕位置不变，仅深度更靠前）。
                val dx = px - cameraPosition.x
                val dy = py - cameraPosition.y
                val dz = pz - cameraPosition.z
                poseStack.translate(dx * (1.0 - FRONT_FRACTION), dy * (1.0 - FRONT_FRACTION), dz * (1.0 - FRONT_FRACTION))
                poseStack.mulPose(cameraOrientation)
                poseStack.scale(-SCALE, -SCALE, SCALE)
                // 在 billboard 局部空间施加摆动与倾斜：横向平移恒为屏幕水平，倾斜绕屏幕法线，读作“晃动/抖动”。
                poseStack.translate(swayLocal.toDouble(), 0.0, 0.0)
                poseStack.mulPose(Quaternionf().rotationZ(tiltRad))
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture)) { pose, consumer ->
                    HeartGraphics.quad(consumer, pose.pose(), -halfSize, -halfSize, halfSize, halfSize, color, p.flipU)
                }
            } finally {
                poseStack.popPose()
            }
        }
    }
}
