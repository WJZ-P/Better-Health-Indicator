package dev.wjz.betterhealthindicator.client.hud

import dev.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import dev.wjz.betterhealthindicator.config.ConfigManager
import dev.wjz.betterhealthindicator.config.PanelCorner
import dev.wjz.betterhealthindicator.client.render.EntitySelector
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.ceil

/**
 * 屏幕角落血量面板：框 + 关注生物的实时 3D 模型 + 名字与血量信息。
 *
 * 通过 Fabric [HudElementRegistry] 注册，在 HUD 提取阶段（[HudElement.extractRenderState]）绘制，
 * 实体模型复用原版 [InventoryScreen.extractEntityInInventoryFollowsMouse]。
 */
object HealthPanelHud {
    private const val PANEL_WIDTH = 120
    private const val PANEL_HEIGHT = 46
    private const val MARGIN = 6
    private const val PADDING = 3

    private const val PANEL_BACKGROUND = 0x90101018.toInt()
    private const val BAR_BACKGROUND = 0xC0202020.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    fun register() {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("better_health_indicator", "health_panel"),
            HudElement { graphics, delta ->
                val config = ConfigManager.config
                if (!config.enabled || !config.panelEnabled) return@HudElement

                val minecraft = Minecraft.getInstance()
                if (minecraft.options.hideGui) return@HudElement

                val tickProgress = delta.getGameTimeDeltaPartialTick(false)
                val frame = EntitySelector.buildFrame(minecraft, config, tickProgress) ?: return@HudElement
                val target = EntitySelector.pickPanelTarget(frame) ?: return@HudElement

                val panelX = when (config.panelCorner) {
                    PanelCorner.TOP_LEFT -> MARGIN
                    PanelCorner.TOP_RIGHT -> graphics.guiWidth() - PANEL_WIDTH - MARGIN
                }
                val panelY = MARGIN

                graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_BACKGROUND)

                val modelX0 = panelX + PADDING
                val modelY0 = panelY + PADDING
                val modelBox = PANEL_HEIGHT - PADDING * 2
                val modelX1 = modelX0 + modelBox
                val modelY1 = modelY0 + modelBox

                if (config.panelShowModel) {
                    renderEntityModel(graphics, target, modelX0, modelY0, modelX1, modelY1)
                }

                val font = minecraft.font
                val textX = modelX1 + 6
                graphics.text(font, target.displayName, textX, panelY + 6, TEXT_COLOR, false)

                val healthRatio = (target.health / target.maxHealth).coerceIn(0.0f, 1.0f)
                val barX0 = textX
                val barY0 = panelY + 20
                val barX1 = panelX + PANEL_WIDTH - PADDING - 3
                val barY1 = barY0 + 6
                graphics.fill(barX0, barY0, barX1, barY1, BAR_BACKGROUND)
                val fillX1 = barX0 + ((barX1 - barX0) * healthRatio).toInt()
                graphics.fill(barX0, barY0, fillX1, barY1, healthColor(healthRatio))

                val healthText = Component.literal("${ceil(target.health).toInt()} / ${ceil(target.maxHealth).toInt()}")
                graphics.text(font, healthText, textX, barY1 + 3, TEXT_COLOR, false)
            },
        )
        BetterHealthIndicatorLogger.info("Health panel HUD registered.")
    }

    private const val MODEL_YAW = 35.0f
    private const val MODEL_PITCH = -8.0f

    private fun renderEntityModel(
        graphics: GuiGraphicsExtractor,
        entity: LivingEntity,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ) {
        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        val renderState = dispatcher.getRenderer(entity).createRenderState(entity, 1.0f)
        renderState.shadowPieces.clear()
        renderState.outlineColor = 0
        if (renderState is LivingEntityRenderState) {
            renderState.bodyRot = 180.0f + MODEL_YAW
            renderState.yRot = MODEL_YAW
            renderState.xRot = if (renderState.pose != Pose.FALL_FLYING) MODEL_PITCH else 0.0f
            renderState.boundingBoxWidth = renderState.boundingBoxWidth / renderState.scale
            renderState.boundingBoxHeight = renderState.boundingBoxHeight / renderState.scale
            renderState.scale = 1.0f
        }

        val boxHeight = (y1 - y0).toFloat()
        val size = (boxHeight * 0.85f / entity.bbHeight.coerceAtLeast(0.6f)).coerceIn(5.0f, 45.0f)
        val translation = Vector3f(0.0f, renderState.boundingBoxHeight / 2.0f + 0.0625f, 0.0f)
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
        val cameraTilt = Quaternionf().rotateX(MODEL_PITCH * (Math.PI.toFloat() / 180.0f))
        rotation.mul(cameraTilt)
        graphics.entity(renderState, size, translation, rotation, cameraTilt, x0, y0, x1, y1)
    }

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
        return (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or 48
    }
}
