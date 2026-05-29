package dev.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf

/**
 * 在生物头顶提交一个面向摄像机的血条。
 *
 * 26.1 的世界渲染采用 extraction/submit 两阶段管线，因此这里在 [LevelRenderEvents.COLLECT_SUBMITS]
 * 阶段把几何交给 [SubmitNodeCollector.submitCustomGeometry]，由原版渲染系统在正确的投影、深度与批次下统一绘制。
 */
object EntityHealthBarRenderer {
    private val config = HealthBarConfig()

    private var firstFireLogged = false
    private var lastStatsLogMs = 0L
    private var geometryCallbackCount = 0
    private var lastGeomLogMs = 0L

    fun register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(
            LevelRenderEvents.CollectSubmits { context -> collectSubmits(context) },
        )
        BetterHealthIndicatorLogger.info("Entity health bar renderer registered.")
    }

    private fun collectSubmits(context: LevelRenderContext) {
        if (config.debug && !firstFireLogged) {
            firstFireLogged = true
            BetterHealthIndicatorLogger.info("COLLECT_SUBMITS fired for the first time (event wiring OK).")
        }

        val poseStack = context.poseStack()
        val collector = context.submitNodeCollector()
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val camera = minecraft.gameRenderer.mainCamera
        val cameraPosition = camera.position()
        val cameraOrientation = camera.rotation()
        val tickProgress = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)

        val now = System.currentTimeMillis()
        val doLog = config.debug && now - lastStatsLogMs >= 2000L

        var livingCount = 0
        var submittedCount = 0
        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity) {
                continue
            }

            livingCount++
            val pass = shouldRender(entity, minecraft, cameraPosition)

            if (doLog && livingCount == 1) {
                BetterHealthIndicatorLogger.info(
                    "inspect first living: type={}, isSelf={}, alive={}, invisible={}, hp={}/{}, distSq={}, maxDistSq={}, pass={}",
                    entity.type,
                    entity === minecraft.player,
                    entity.isAlive,
                    entity.isInvisible,
                    entity.health,
                    entity.maxHealth,
                    entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z),
                    config.maxDistanceSquared,
                    pass,
                )
            }

            if (pass) {
                submitHealthBar(entity, collector, poseStack, cameraPosition, cameraOrientation, tickProgress)
                submittedCount++
            }
        }

        if (doLog) {
            lastStatsLogMs = now
            BetterHealthIndicatorLogger.info(
                "stats: living={}, submitted={}, geometryCallbacks(last~2s)={}, poseStackNull={}",
                livingCount,
                submittedCount,
                geometryCallbackCount,
                false,
            )
            geometryCallbackCount = 0
        }
    }

    private fun shouldRender(entity: LivingEntity, minecraft: Minecraft, cameraPosition: Vec3): Boolean {
        if (!config.showSelf && entity === minecraft.player) {
            return false
        }

        if (!entity.isAlive || entity.isInvisible) {
            return false
        }

        val maxHealth = entity.maxHealth
        if (maxHealth <= 0.0f) {
            return false
        }

        if (!config.showFullHealthEntities && entity.health >= maxHealth) {
            return false
        }

        return entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) <= config.maxDistanceSquared
    }

    private fun submitHealthBar(
        entity: LivingEntity,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        cameraPosition: Vec3,
        cameraOrientation: Quaternionf,
        tickProgress: Float,
    ) {
        val position = entity.getPosition(tickProgress)
        val healthRatio = (entity.health / entity.maxHealth).coerceIn(0.0f, 1.0f)
        val fillWidth = config.width * healthRatio

        val left = -config.width / 2.0f
        val right = config.width / 2.0f
        val top = -config.height / 2.0f
        val bottom = config.height / 2.0f
        val foreground = healthColor(healthRatio)

        poseStack.pushPose()
        try {
            poseStack.translate(
                position.x - cameraPosition.x,
                position.y - cameraPosition.y + entity.bbHeight + config.yOffset,
                position.z - cameraPosition.z,
            )
            poseStack.mulPose(cameraOrientation)
            poseStack.scale(-config.scale, -config.scale, config.scale)

            collector.submitCustomGeometry(poseStack, RenderTypes.textBackground()) { pose, consumer ->
                geometryCallbackCount++
                val matrix = pose.pose()
                if (config.debug) {
                    val nowGeom = System.currentTimeMillis()
                    if (nowGeom - lastGeomLogMs >= 2000L) {
                        lastGeomLogMs = nowGeom
                        BetterHealthIndicatorLogger.info(
                            "geometry draw: poseTranslate=({}, {}, {}), renderType={}",
                            matrix.m30(),
                            matrix.m31(),
                            matrix.m32(),
                            RenderTypes.textBackground(),
                        )
                    }
                }
                drawQuad(
                    consumer,
                    matrix,
                    left - config.borderSize,
                    top - config.borderSize,
                    right + config.borderSize,
                    bottom + config.borderSize,
                    config.borderColor,
                )
                drawQuad(consumer, matrix, left, top, right, bottom, config.backgroundColor)
                drawQuad(consumer, matrix, left, top, left + fillWidth, bottom, foreground)
            }
        } finally {
            poseStack.popPose()
        }
    }

    private fun drawQuad(
        consumer: VertexConsumer,
        matrix: Matrix4f,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
    ) {
        consumer.addVertex(matrix, left, bottom, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        consumer.addVertex(matrix, right, bottom, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        consumer.addVertex(matrix, right, top, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        consumer.addVertex(matrix, left, top, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
    }

    /** 血量从满到空：绿 -> 黄 -> 红。 */
    private fun healthColor(ratio: Float): Int {
        val red: Int
        val green: Int
        if (ratio >= 0.5f) {
            red = ((1.0f - ratio) * 2.0f * 255.0f).toInt()
            green = 255
        } else {
            red = 255
            green = (ratio * 2.0f * 255.0f).toInt()
        }

        return argb(config.foregroundAlpha, red, green, 48)
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }
}
