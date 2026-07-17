package com.wjz.betterhealthindicator.legacy14.hud

import com.mojang.blaze3d.platform.GlStateManager
import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.client.render.AttackTracker
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.PanelCorner
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent
import net.minecraft.world.entity.LivingEntity
import kotlin.math.ceil

/** 1.14 固定管线 HUD：显示准星目标或最近受击目标的名字与血量条。 */
object HealthPanelHud {
    fun register() {
        BetterHealthIndicatorLogger.info("Legacy OpenGL health panel registered.")
    }

    fun renderLegacy(tickProgress: Float) {
        val minecraft = Minecraft.getInstance()
        val config = ConfigManager.config
        if (!config.enabled || !config.panelEnabled || minecraft.options.hideGui) return
        val target = (minecraft.crosshairPickEntity as? LivingEntity) ?: AttackTracker.tracked(config) ?: return
        if (!target.isAlive || target.isInvisible || target.maxHealth <= 0.0f) return

        val width = 126
        val height = 32
        val x = if (config.panelCorner == PanelCorner.TOP_RIGHT) minecraft.window.guiScaledWidth - width - 3 else 3
        val y = 3
        val ratio = (target.health / target.maxHealth).coerceIn(0.0f, 1.0f)
        val barX0 = x + 6
        val barX1 = x + width - 6
        val barY0 = y + 19
        val barY1 = y + 26
        val fillX = barX0 + ((barX1 - barX0) * ratio).toInt()
        val color = when {
            ratio > 0.5f -> 0xFF55FF55.toInt()
            ratio > 0.25f -> 0xFFFFFF55.toInt()
            else -> 0xFFFF5555.toInt()
        }
        val label = "${target.displayName.string}  ${ceil(target.health).toInt()} / ${ceil(target.maxHealth).toInt()}"

        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.disableDepthTest()
        GuiComponent.fill(x, y, x + width, y + height, 0xB0202020.toInt())
        GuiComponent.fill(x, y, x + width, y + 1, 0xFFE0E0E0.toInt())
        GuiComponent.fill(barX0 - 1, barY0 - 1, barX1 + 1, barY1 + 1, 0xFF000000.toInt())
        GuiComponent.fill(barX0, barY0, barX1, barY1, 0xFF303030.toInt())
        GuiComponent.fill(barX0, barY0, fillX, barY1, color)
        minecraft.font.drawShadow(label, (x + 6).toFloat(), (y + 6).toFloat(), 0xFFFFFF)
        GlStateManager.enableDepthTest()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }
}
