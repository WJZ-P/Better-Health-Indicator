package com.wjz.betterhealthindicator.legacy14.render

import com.mojang.blaze3d.platform.GlStateManager
import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.client.render.AttackTracker
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.DisplayMode
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.renderer.culling.Culler
import net.minecraft.world.entity.LivingEntity
import kotlin.math.ceil

/** 1.14 固定管线后端：保留实体筛选、名字、数值和动态血条。 */
object EntityHealthBarRenderer {
    private val shownThisFrame = HashSet<Int>()

    fun register() {
        BetterHealthIndicatorLogger.info("Legacy OpenGL entity health renderer registered.")
    }

    fun shouldHideVanillaName(entityId: Int): Boolean {
        val config = ConfigManager.config
        return config.enabled && config.headBarEnabled && config.hideVanillaNameTag && shownThisFrame.contains(entityId)
    }

    fun renderLegacy(camera: Camera, culler: Culler, tickProgress: Float) {
        shownThisFrame.clear()
        val config = ConfigManager.config
        if (!config.enabled || !config.headBarEnabled) return

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val cameraPosition = camera.position
        for (raw in level.entitiesForRendering()) {
            val entity = raw as? LivingEntity ?: continue
            if (!entity.isAlive || entity.isInvisible) continue
            if (!config.showSelf && entity === minecraft.player) continue
            if (!config.showFullHealthEntities && entity.health >= entity.maxHealth) continue
            if (entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) > config.maxDistanceSquared) continue
            if (config.displayMode == DisplayMode.LOOKING_AT &&
                entity !== minecraft.crosshairPickEntity && !AttackTracker.isTracked(entity, config)
            ) continue
            if (config.displayMode == DisplayMode.ON_SCREEN && !culler.isVisible(entity.boundingBox.inflate(0.25))) continue

            shownThisFrame.add(entity.id)
            draw(entity, camera, tickProgress)
        }
    }

    private fun draw(entity: LivingEntity, camera: Camera, tickProgress: Float) {
        val minecraft = Minecraft.getInstance()
        val config = ConfigManager.config
        val cameraPosition = camera.position
        val x = lerp(tickProgress, entity.xOld, entity.x) - cameraPosition.x
        val y = lerp(tickProgress, entity.yOld, entity.y) - cameraPosition.y + entity.bbHeight + config.yOffset
        val z = lerp(tickProgress, entity.zOld, entity.z) - cameraPosition.z
        val health = entity.health
        val maximum = entity.maxHealth.coerceAtLeast(1.0f)
        val ratio = (health / maximum).coerceIn(0.0f, 1.0f)
        val name = entity.displayName.string
        val healthText = "${ceil(health).toInt()} / ${ceil(maximum).toInt()}"
        val label = when {
            config.showName && config.showHealthText -> "$name  $healthText"
            config.showName -> name
            config.showHealthText -> healthText
            else -> ""
        }
        val font = minecraft.font
        val width = maxOf(40, font.width(label) + 4)
        val half = width / 2
        val fill = (-half + (width * ratio).toInt()).coerceAtMost(half)
        val color = when {
            ratio > 0.5f -> 0xE055FF55.toInt()
            ratio > 0.25f -> 0xE0FFFF55.toInt()
            else -> 0xE0FF5555.toInt()
        }

        GlStateManager.pushMatrix()
        GlStateManager.translated(x, y, z)
        GlStateManager.rotatef(-camera.yRot, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotatef(camera.xRot, 1.0f, 0.0f, 0.0f)
        GlStateManager.scaled(-config.scale.toDouble(), -config.scale.toDouble(), config.scale.toDouble())
        GlStateManager.enableBlend()
        if (config.occludeBehindWalls) GlStateManager.enableDepthTest() else GlStateManager.disableDepthTest()
        GlStateManager.depthMask(false)
        GuiComponent.fill(-half - 1, -2, half + 1, 4, config.borderColor)
        GuiComponent.fill(-half, -1, half, 3, config.backgroundColor)
        GuiComponent.fill(-half, -1, fill, 3, color)
        if (label.isNotEmpty()) font.drawShadow(label, (-font.width(label) / 2).toFloat(), -13.0f, 0xFFFFFF)
        GlStateManager.depthMask(true)
        GlStateManager.enableDepthTest()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    private fun lerp(t: Float, from: Double, to: Double): Double = from + (to - from) * t.toDouble()
}
