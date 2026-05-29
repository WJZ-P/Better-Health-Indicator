package dev.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

object EntityHealthBarRenderer {
    private val settings = HealthBarSettings()

    fun register() {
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(
            LevelRenderEvents.AfterSolidFeatures { context -> render(context) },
        )
        BetterHealthIndicatorLogger.info("Entity health bar renderer registered.")
    }

    private fun render(context: LevelRenderContext) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val poseStack = context.poseStack()
        val bufferSource = context.bufferSource()
        val camera = minecraft.gameRenderer.mainCamera
        val cameraPosition = camera.position()
        val tickProgress = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val vertexConsumer = bufferSource.getBuffer(RenderTypes.textBackground())

        var rendered = false
        for (entity in level.entitiesForRendering()) {
            if (entity is LivingEntity && shouldRender(entity, minecraft, cameraPosition)) {
                renderHealthBar(entity, camera, poseStack, vertexConsumer, tickProgress, cameraPosition)
                rendered = true
            }
        }

        if (rendered) {
            bufferSource.endBatch()
        }
    }

    private fun shouldRender(entity: LivingEntity, minecraft: Minecraft, cameraPosition: Vec3): Boolean {
        if (!settings.showSelf && entity == minecraft.player) {
            return false
        }

        if (!entity.isAlive || entity.isInvisible) {
            return false
        }

        val maxHealth = entity.maxHealth
        if (maxHealth <= 0.0f) {
            return false
        }

        if (!settings.showFullHealthEntities && entity.health >= maxHealth) {
            return false
        }

        return entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) <= settings.maxDistanceSquared
    }

    private fun renderHealthBar(
        entity: LivingEntity,
        camera: Camera,
        poseStack: PoseStack,
        vertexConsumer: VertexConsumer,
        tickProgress: Float,
        cameraPosition: Vec3,
    ) {
        val entityPosition = entity.getPosition(tickProgress)
        val healthRatio = (entity.health / entity.maxHealth).coerceIn(0.0f, 1.0f)
        val fillWidth = settings.width * healthRatio

        poseStack.pushPose()
        try {
            poseStack.translate(
                entityPosition.x - cameraPosition.x,
                entityPosition.y - cameraPosition.y + entity.bbHeight + settings.yOffset,
                entityPosition.z - cameraPosition.z,
            )
            poseStack.mulPose(camera.rotation())
            poseStack.scale(-settings.scale, -settings.scale, settings.scale)

            val matrix = poseStack.last().pose()
            val left = -settings.width / 2.0f
            val right = settings.width / 2.0f
            val top = -settings.height / 2.0f
            val bottom = settings.height / 2.0f

            drawQuad(
                vertexConsumer,
                matrix,
                left - settings.borderSize,
                top - settings.borderSize,
                right + settings.borderSize,
                bottom + settings.borderSize,
                settings.borderColor,
            )
            drawQuad(vertexConsumer, matrix, left, top, right, bottom, settings.backgroundColor)
            drawQuad(vertexConsumer, matrix, left, top, left + fillWidth, bottom, healthColor(healthRatio))
        } finally {
            poseStack.popPose()
        }
    }

    private fun drawQuad(
        vertexConsumer: VertexConsumer,
        matrix: Matrix4f,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
    ) {
        vertexConsumer.addVertex(matrix, left, bottom, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        vertexConsumer.addVertex(matrix, right, bottom, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        vertexConsumer.addVertex(matrix, right, top, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        vertexConsumer.addVertex(matrix, left, top, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
    }

    private fun healthColor(healthRatio: Float): Int {
        val red = ((1.0f - healthRatio) * 255.0f).toInt().coerceIn(0, 255)
        val green = (healthRatio * 255.0f).toInt().coerceIn(0, 255)

        return argb(settings.foregroundAlpha, red, green, 48)
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }

    private data class HealthBarSettings(
        val maxDistance: Double = 48.0,
        val width: Float = 32.0f,
        val height: Float = 4.0f,
        val borderSize: Float = 1.0f,
        val yOffset: Double = 0.45,
        val scale: Float = 0.025f,
        val showSelf: Boolean = false,
        val showFullHealthEntities: Boolean = true,
        val foregroundAlpha: Int = 224,
        val backgroundColor: Int = 0xAA202020.toInt(),
        val borderColor: Int = 0xCC000000.toInt(),
    ) {
        val maxDistanceSquared: Double = maxDistance * maxDistance
    }
}
