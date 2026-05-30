package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import java.util.Random

/**
 * 掉血爱心粒子：生物掉血时，在血条所在世界位置生成会下落、渐隐的爱心。
 *
 * 粒子生成后与实体解耦（记录绝对世界坐标，仅受自身物理影响），模仿原版掉血爱心的表现。
 * 物理按帧间真实时间推进，渲染走 [RenderTypes.entityTranslucent] 以支持透明度淡出。
 */
object HeartParticleManager {
    private const val GRAVITY = 5.0
    private const val MAX_AGE = 1.25f
    private const val SCALE = 0.025f
    private const val MAX_PARTICLES = 1024
    private const val MAX_FRAME_DELTA = 0.1f

    private class Particle(
        var x: Double,
        var y: Double,
        var z: Double,
        var vx: Double,
        var vy: Double,
        var vz: Double,
        var age: Float,
        val half: Boolean,
    )

    private val particles = ArrayList<Particle>()
    private val random = Random()
    private var lastNanos = 0L

    fun spawn(x: Double, y: Double, z: Double, count: Int, half: Boolean) {
        repeat(count) {
            if (particles.size >= MAX_PARTICLES) return
            particles.add(
                Particle(
                    x + (random.nextDouble() - 0.5) * 0.5,
                    y,
                    z + (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.6,
                    0.35 + random.nextDouble() * 0.35,
                    (random.nextDouble() - 0.5) * 0.6,
                    0.0f,
                    half,
                ),
            )
        }
    }

    /** 按帧间真实时间推进所有粒子物理，移除过期粒子。 */
    fun update() {
        val now = System.nanoTime()
        if (lastNanos == 0L) {
            lastNanos = now
            return
        }
        var dt = ((now - lastNanos) / 1.0e9).toFloat()
        lastNanos = now
        if (dt <= 0.0f) return
        if (dt > MAX_FRAME_DELTA) dt = MAX_FRAME_DELTA

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.vy -= GRAVITY * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            p.age += dt
            if (p.age >= MAX_AGE) iterator.remove()
        }
    }

    fun isEmpty(): Boolean = particles.isEmpty()

    fun render(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        cameraPosition: Vec3,
        cameraOrientation: Quaternionf,
    ) {
        val halfSize = HeartGraphics.SIZE / 2.0f
        for (p in particles) {
            val alpha = (255.0f * (1.0f - p.age / MAX_AGE)).toInt().coerceIn(0, 255)
            val color = (alpha shl 24) or 0xFFFFFF
            val texture = if (p.half) HeartGraphics.HALF else HeartGraphics.FULL
            poseStack.pushPose()
            try {
                poseStack.translate(p.x - cameraPosition.x, p.y - cameraPosition.y, p.z - cameraPosition.z)
                poseStack.mulPose(cameraOrientation)
                poseStack.scale(-SCALE, -SCALE, SCALE)
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture)) { pose, consumer ->
                    HeartGraphics.quad(consumer, pose.pose(), -halfSize, -halfSize, halfSize, halfSize, color)
                }
            } finally {
                poseStack.popPose()
            }
        }
    }
}
