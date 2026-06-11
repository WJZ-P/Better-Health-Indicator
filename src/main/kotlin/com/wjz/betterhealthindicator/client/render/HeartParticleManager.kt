package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Random
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

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
    private const val MAX_AGE_TICKS = 28 // 约 1.4s（基准寿命）
    private const val MAX_AGE_JITTER_TICKS = 5 // 每颗粒子寿命在基准上下随机波动 ±此值（tick），打破整批同时消失的整齐感；0 即不随机
    private const val FADE_TICKS = 5 // 末尾 0.25s 才开始淡出（其余时间不透明，契合像素观感）
    private const val SCALE = 0.025f
    private const val MAX_PARTICLES = 128

    // 渲染时沿“相机→粒子”视线把粒子朝相机方向挪动的距离比例：
    // billboard 沿视线平移不改变屏幕位置，仅令其深度严格更靠前，从而覆盖（写深度的）血条爱心，避免共面被遮挡。
    private const val FRONT_FRACTION = 0.05

    // 阻尼谐振（“弹簧”）参数。
    private const val SWAY_OMEGA = 0.85f // 主摆动角频率（rad/tick，已放缓）
    private const val SWAY_DAMP = 0.10f // 每 tick 振幅衰减系数
    private const val JITTER_OMEGA = 2.6f // 重档高频抖动角频率
    private const val DEG_TO_RAD = 0.017453292f

    // —— 死亡时 container 破碎碎片 ——
    private const val SHARD_GRID = 2 // 每颗心切成 GRID×GRID 片
    private const val SHARD_GRAVITY = 0.01 // 碎片重力（比爱心略重，像碎渣坠落）
    private const val SHARD_DRAG = 0.92 // 碎片横向阻力（保留较多横向动能，使其明显朝左右飞出而非很快停住）
    private const val SHARD_MAX_AGE_TICKS = 24 // 约 1.2s
    private const val SHARD_FADE_TICKS = 6 // 末段淡出
    private const val SHARD_EXPLODE_SPEED = 0.12 // 径向爆裂初速（格/tick）
    private const val SHARD_HORIZONTAL_BOOST = 1.0 // 横向（屏幕左右）分量额外放大，强化“向两边飞”的破碎感
    private const val SHARD_DIR_JITTER = 0.5 // 爆裂方向随机扰动幅度（打破完美放射状，使破碎更自然）
    private const val SHARD_SIZE_JITTER = 0.35 // 碎片大小随机幅度（±比例）
    private const val SHARD_UP_POP = 0.012 // 额外向上初速，使碎片先略扬起再坠落（不喧宾夺主）
    private const val SHARD_MAX = 256 // 碎片数量上限（独立于爱心粒子）

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
            damage > heavy -> ParticleStyle(5.5f, 0.17f * s, 0.36f * s, 28.0f * s, 0.14f * s, 2.5, 0.45)
            damage >= medium -> ParticleStyle(4.0f, 0.12f * s, 0.24f * s, 9.0f * s, 0.0f, 2.0, 0.7)
            else -> ParticleStyle(3.0f, 0.08f * s, 0.07f * s, 5.0f * s, 0.0f, 1.5, 1.0)
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
        val maxAge: Int, // 本颗寿命（tick）：基准 ± 随机波动，错开消失时机
    ) {
        // 上一 tick 的位置，供渲染插值；生成时与当前位置相同，避免首帧跳变。
        var xo = x
        var yo = y
        var zo = z
        var age = 0
        var ageo = 0
    }

    /**
     * container 破碎碎片：取心形贴图一格 UV 的小四边形，向外爆裂、翻滚自旋、坠落、淡出。
     * 物理与爱心粒子同为 tick 定步 + partialTick 插值（含旋转角插值），随暂停/冻结自然停摆。
     */
    private class Shard(
        var x: Double,
        var y: Double,
        var z: Double,
        var vx: Double,
        var vy: Double,
        var vz: Double,
        val texture: Identifier,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val halfExtent: Float, // 碎片四边形半边长（局部单位）
        var rot: Float, // 当前自旋角（弧度）
        val rotVel: Float, // 每 tick 自旋角速度
        val maxAge: Int, // 本片寿命（tick），随机化以错落消失
    ) {
        var xo = x
        var yo = y
        var zo = z
        var roto = rot
        var age = 0
        var ageo = 0
    }

    private val particles = ArrayList<Particle>()
    private val shards = ArrayList<Shard>()
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
        // 寿命随机波动：基准上下 ±MAX_AGE_JITTER_TICKS，错开整批粒子的消失时机；下限确保留得住淡出段。
        val jitter = if (MAX_AGE_JITTER_TICKS > 0) random.nextInt(MAX_AGE_JITTER_TICKS * 2 + 1) - MAX_AGE_JITTER_TICKS else 0
        val maxAge = (MAX_AGE_TICKS + jitter).coerceAtLeast(FADE_TICKS + 1)
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
                maxAge,
            ),
        )
    }

    /**
     * 在某颗心的世界中心处，把 container 心形按 GRID×GRID 切片爆裂成碎片。
     * @param centerX,centerY,centerZ 该颗心的世界中心（与血条上那颗心对齐）。
     * @param orientation 与血条 billboard 相同的相机朝向，用于把局部格点摆到世界、并求径向爆裂方向。
     */
    fun spawnContainerShards(
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        orientation: Quaternionf,
        texture: Identifier,
    ) {
        val size = HeartGraphics.SIZE
        val cell = size / SHARD_GRID
        val half = cell / 2.0f
        for (i in 0 until SHARD_GRID) {
            for (j in 0 until SHARD_GRID) {
                if (shards.size >= SHARD_MAX) return
                // 格中心的局部坐标（-size/2..size/2）；billboard 用 scale(-,-,+)，故 x、y 取负号映射到世界。
                val localX = (i + 0.5f) / SHARD_GRID * size - size / 2.0f
                val localY = (j + 0.5f) / SHARD_GRID * size - size / 2.0f
                val off = Vector3f(-SCALE * localX, -SCALE * localY, 0.0f)
                orientation.transform(off)
                // 径向爆裂方向 = 该格相对心中心的世界方向（即 off 的方向）；中心格退化为随机方向。
                val len = off.length()
                val dirX: Double
                val dirY: Double
                val dirZ: Double
                if (len > 1.0e-6f) {
                    dirX = off.x.toDouble() / len
                    dirY = off.y.toDouble() / len
                    dirZ = off.z.toDouble() / len
                } else {
                    val a = random.nextDouble() * Math.PI * 2.0
                    dirX = cos(a)
                    dirY = 0.0
                    dirZ = sin(a)
                }
                // 方向随机扰动：在径向基础上加入随机偏移再归一化，打破完美放射状，破碎更自然。
                var jx = dirX + (random.nextDouble() - 0.5) * SHARD_DIR_JITTER
                var jy = dirY + (random.nextDouble() - 0.5) * SHARD_DIR_JITTER
                var jz = dirZ + (random.nextDouble() - 0.5) * SHARD_DIR_JITTER
                val jl = sqrt(jx * jx + jy * jy + jz * jz)
                if (jl > 1.0e-6) {
                    jx /= jl
                    jy /= jl
                    jz /= jl
                }
                val speed = SHARD_EXPLODE_SPEED * (0.5 + random.nextDouble() * 0.9)
                val upPop = SHARD_UP_POP * (0.4 + random.nextDouble() * 1.2)
                val sizeFactor = 1.0f + ((random.nextDouble() - 0.5) * 2.0 * SHARD_SIZE_JITTER).toFloat()
                val maxAge = (SHARD_MAX_AGE_TICKS * (0.8 + random.nextDouble() * 0.4)).toInt().coerceAtLeast(SHARD_FADE_TICKS + 1)
                // jx/jz 是世界水平分量（屏幕左右），单独放大让碎片明显朝两边飞；jy 为竖直分量。
                shards.add(
                    Shard(
                        centerX + off.x,
                        centerY + off.y,
                        centerZ + off.z,
                        jx * speed * SHARD_HORIZONTAL_BOOST,
                        jy * speed + upPop, // 叠加（随机）向上初速，先扬起再坠落
                        jz * speed * SHARD_HORIZONTAL_BOOST,
                        texture,
                        i.toFloat() / SHARD_GRID,
                        j.toFloat() / SHARD_GRID,
                        (i + 1).toFloat() / SHARD_GRID,
                        (j + 1).toFloat() / SHARD_GRID,
                        half * sizeFactor,
                        (random.nextDouble() * Math.PI * 2.0).toFloat(),
                        ((random.nextDouble() - 0.5) * 0.8).toFloat(), // 随机翻滚自旋
                        maxAge,
                    ),
                )
            }
        }
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
            if (p.age >= p.maxAge) iterator.remove()
        }

        val shardIterator = shards.iterator()
        while (shardIterator.hasNext()) {
            val s = shardIterator.next()
            s.xo = s.x
            s.yo = s.y
            s.zo = s.z
            s.roto = s.rot
            s.ageo = s.age
            s.vy -= SHARD_GRAVITY
            s.vx *= SHARD_DRAG
            s.vz *= SHARD_DRAG
            s.x += s.vx
            s.y += s.vy
            s.z += s.vz
            s.rot += s.rotVel
            s.age++
            if (s.age >= s.maxAge) shardIterator.remove()
        }
    }

    fun isEmpty(): Boolean = particles.isEmpty() && shards.isEmpty()

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

            // 仅在末尾 FADE_TICKS 内淡出，其余时间完全不透明（按本颗随机寿命 p.maxAge 计算）。
            val fadeStart = p.maxAge - FADE_TICKS
            val fadeFactor = if (ageRender <= fadeStart) {
                1.0f
            } else {
                ((p.maxAge - ageRender) / FADE_TICKS).coerceIn(0.0f, 1.0f)
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

        renderShards(collector, poseStack, cameraPosition, cameraOrientation, partialTick)
    }

    private fun renderShards(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        cameraPosition: Vec3,
        cameraOrientation: Quaternionf,
        partialTick: Float,
    ) {
        for (s in shards) {
            val px = s.xo + (s.x - s.xo) * partialTick
            val py = s.yo + (s.y - s.yo) * partialTick
            val pz = s.zo + (s.z - s.zo) * partialTick
            val ageRender = s.ageo + (s.age - s.ageo) * partialTick
            val rot = s.roto + (s.rot - s.roto) * partialTick

            val fadeStart = s.maxAge - SHARD_FADE_TICKS
            val fadeFactor = if (ageRender <= fadeStart) {
                1.0f
            } else {
                ((s.maxAge - ageRender) / SHARD_FADE_TICKS).coerceIn(0.0f, 1.0f)
            }
            val alpha = (255.0f * fadeFactor).toInt().coerceIn(0, 255)
            val color = (alpha shl 24) or 0xFFFFFF
            val h = s.halfExtent

            poseStack.pushPose()
            try {
                val dx = px - cameraPosition.x
                val dy = py - cameraPosition.y
                val dz = pz - cameraPosition.z
                poseStack.translate(dx * (1.0 - FRONT_FRACTION), dy * (1.0 - FRONT_FRACTION), dz * (1.0 - FRONT_FRACTION))
                poseStack.mulPose(cameraOrientation)
                poseStack.mulPose(Quaternionf().rotationZ(rot)) // 翻滚自旋
                poseStack.scale(-SCALE, -SCALE, SCALE)
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(s.texture)) { pose, consumer ->
                    HeartGraphics.quadUv(consumer, pose.pose(), -h, -h, h, h, s.u0, s.v0, s.u1, s.v1, color)
                }
            } finally {
                poseStack.popPose()
            }
        }
    }
}
