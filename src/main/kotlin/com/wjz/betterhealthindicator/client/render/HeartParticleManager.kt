package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import java.util.Random
import kotlin.math.cos
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
    private const val HORIZONTAL_DRAG = 0.9 // 横向速度每 tick 的保留比例：出生爆发逸散后迅速减速、平稳下落
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
     * @param spread 出生瞬间向四周爆发逸散的横向初速（格/tick 量级），越大炸得越开。
     * @param swayAmp 横向摆动幅度（占爱心宽度比例）。
     * @param tiltDeg 倾斜摆动幅度（度）。
     * @param jitterAmp 高频抖动幅度（占爱心宽度比例，仅重档 > 0）。
     * @param riseGravityScale 上升段重力倍率（越大越快冲到顶点；高档调大让弹起更迅猛）。
     * @param fallGravityScale 下落段重力倍率（越小落得越慢；高档调小让爱心在顶点附近“悬停”缓降，更壮观）。
     */
    class ParticleStyle(
        val springScale: Float,
        val spread: Float,
        val swayAmp: Float,
        val tiltDeg: Float,
        val jitterAmp: Float,
        val riseGravityScale: Double,
        val fallGravityScale: Double,
    )

    /**
     * 按本次总伤害选择掉落风格：
     * - 轻档（< medium）：小幅逸散、轻微晃动，弹簧“快速蹦一下”就落回（表示造成一点点伤害）；
     * - 中档（[medium, heavy]）：中等逸散与晃动，上升略快、下落略缓；
     * - 重档（> heavy）：大幅爆裂逸散 + 加大弹簧 + 高频抖动；上升迅猛冲顶、下落明显放缓“悬停”缓降，体感壮观。
     * @param shakeScale 全局抖动幅度倍率（缩放 spread/sway/tilt/jitter，不改变弹起高度与升降节奏）。
     */
    fun styleFor(damage: Float, medium: Float, heavy: Float, shakeScale: Float): ParticleStyle {
        val s = shakeScale.coerceAtLeast(0.0f)
        return when {
            damage > heavy -> ParticleStyle(3.0f, 0.20f * s, 0.46f * s, 28.0f * s, 0.14f * s, 2.5, 0.45)
            damage >= medium -> ParticleStyle(2.0f, 0.12f * s, 0.14f * s, 9.0f * s, 0.0f, 2.0, 0.7)
            else -> ParticleStyle(1.5f, 0.08f * s, 0.07f * s, 5.0f * s, 0.0f, 1.5, 1.0)
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
    /**
     * @param biasX,biasZ 横向逸散的方向偏置（世界水平单位向量 × 方向）；
     *        半心传入“被打掉那侧”的方向，使其主要朝该侧飞；满心传 0 表示无偏置、四散。
     */
    fun spawn(
        x: Double,
        y: Double,
        z: Double,
        texture: Identifier,
        flipU: Boolean,
        style: ParticleStyle,
        biasX: Double = 0.0,
        biasZ: Double = 0.0,
    ) {
        if (particles.size >= MAX_PARTICLES) return
        // 出生瞬间“爆裂逸散”：随机水平方向 + 随机大小的横向初速，配合 tick 阻力先炸开再减速。
        val angle = random.nextDouble() * Math.PI * 2.0
        val horizontalSpeed = style.spread * (0.6 + random.nextDouble() * 0.4)
        var vx = cos(angle) * horizontalSpeed
        var vz = sin(angle) * horizontalSpeed
        if (biasX != 0.0 || biasZ != 0.0) {
            // 半心：主要朝被打掉那侧飞，仅叠加少量随机散开，方向感明确。
            val dirSpeed = style.spread * (0.9 + random.nextDouble() * 0.5)
            vx = biasX * dirSpeed + vx * 0.35
            vz = biasZ * dirSpeed + vz * 0.35
        }
        particles.add(
            Particle(
                x,
                y,
                z,
                vx,
                (0.014 + random.nextDouble() * 0.012) * style.springScale, // 向上“蹦出”，随后慢慢落下
                vz,
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
            // 非对称重力：上升段（vy>0）用更大重力快速冲顶，下落段（vy≤0）用更小重力放缓下落。
            // 伤害越高，上升越迅猛、下落越“悬停”缓降，攻击表现越壮观。
            val gravity = GRAVITY * if (p.vy > 0.0) p.style.riseGravityScale else p.style.fallGravityScale
            p.vy -= gravity
            // 横向阻力：爆发逸散后迅速衰减，使粒子“炸开 → 减速 → 平稳竖直下落”。
            p.vx *= HORIZONTAL_DRAG
            p.vz *= HORIZONTAL_DRAG
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
