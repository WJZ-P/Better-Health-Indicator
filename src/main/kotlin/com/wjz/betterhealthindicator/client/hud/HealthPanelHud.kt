package com.wjz.betterhealthindicator.client.hud

import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.PanelCorner
import com.wjz.betterhealthindicator.client.render.EntityModelExtents
import com.wjz.betterhealthindicator.client.render.EntitySelector
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.min

/**
 * 屏幕角落血量面板：框 + 关注生物的实时 3D 模型 + 名字与血量信息。
 *
 * 通过 Fabric [HudElementRegistry] 注册，在 HUD 提取阶段（[HudElement.extractRenderState]）绘制，
 * 实体模型复用原版 [InventoryScreen.extractEntityInInventoryFollowsMouse]。
 */
object HealthPanelHud {
    private const val PANEL_WIDTH = 120
    private const val PANEL_HEIGHT = 44
    private const val MARGIN = 2
    private const val PADDING = 2

    private const val PANEL_BACKGROUND = 0x90101018.toInt()
    private const val BAR_BACKGROUND = 0xC0202020.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val MODEL_PITCH = -15.0f

    // 模型在渲染框中占用的比例（留少量边距，避免模型网格略超碰撞箱时贴边）。
    private const val MODEL_FILL_RATIO = 0.9f
    private const val MODEL_MIN_SIZE = 5.0f
    private const val MODEL_MAX_SIZE = 45.0f
    // √2：MC 碰撞箱水平足迹为“宽×宽”正方形，绕行到 45° 视角时横向投影最大可达 宽×√2，取此最坏值保证不被裁切。
    private const val FOOTPRINT_DIAGONAL = 1.41421356f

    // 最近被本地玩家攻击的生物与攻击时刻（毫秒墙钟）；用于准星无目标时的兜底显示。
    private var lastAttacked: LivingEntity? = null
    private var lastAttackAtMs: Long = 0L

    fun register() {
        AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
            if (player === Minecraft.getInstance().player && entity is LivingEntity) {
                lastAttacked = entity
                lastAttackAtMs = System.currentTimeMillis()
            }
            InteractionResult.PASS
        }
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("better_health_indicator", "health_panel"),
            HudElement { graphics, delta ->
                val config = ConfigManager.config
                if (!config.enabled || !config.panelEnabled) return@HudElement

                val minecraft = Minecraft.getInstance()
                if (minecraft.options.hideGui) return@HudElement

                val tickProgress = delta.getGameTimeDeltaPartialTick(false)
                val frame = EntitySelector.buildFrame(minecraft, config, tickProgress) ?: return@HudElement
                // 优先准星目标；都没有时，最低优先级兜底显示有效期内的“最近受击生物”。
                val target = EntitySelector.pickPanelTarget(frame) ?: pickAttackedFallback(frame) ?: return@HudElement

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

    /**
     * 兜底目标：在有效期内且仍满足通用渲染条件的“最近受击生物”，否则清空并返回 null。
     * 作为最低优先级，仅当准星没有命中可显示目标时使用。
     */
    private fun pickAttackedFallback(frame: EntitySelector.Frame): LivingEntity? {
        val config = frame.config
        if (!config.panelTrackAttacked) return null
        val attacked = lastAttacked ?: return null

        val elapsed = System.currentTimeMillis() - lastAttackAtMs
        if (elapsed > (config.panelAttackTrackingSeconds * 1000.0).toLong()) {
            lastAttacked = null
            return null
        }
        if (!EntitySelector.isPanelFallbackEligible(attacked, frame)) return null
        return attacked
    }

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
            // bodyRot：身体绝对朝向，跟随“玩家相对生物的水平视角”，使绕行时面板显示对应侧面/背面。
            // yRot：原版语义为“头相对身体的扭转量并取负”（0 即对齐），故只放头自身扭转，绝不能混入身体朝向。
            val partialTick = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
            val headDelta = Mth.degreesDifference(
                Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot),
                Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot),
            )
            renderState.bodyRot = 180.0f + relativeViewYaw(entity)
            renderState.yRot = -headDelta
            renderState.xRot = if (renderState.pose != Pose.FALL_FLYING) MODEL_PITCH else 0.0f
            renderState.boundingBoxWidth /= renderState.scale
            renderState.boundingBoxHeight /= renderState.scale
            renderState.scale = 1.0f
        }

        // 同时按高度与（最坏角度的）宽度求可放入渲染框的最大缩放，取较小者，确保横宽生物也能完整展示不被裁切。
        val boxWidth = (x1 - x0).toFloat()
        val boxHeight = (y1 - y0).toFloat()
        val extents = EntityModelExtents.get(entity)
        // 优先用模型真实网格范围（含头/嘴等凸出网格）；取不到时退化为碰撞箱估算。
        val verticalExtent = (extents?.height ?: entity.bbHeight).coerceAtLeast(0.6f)
        val horizontalExtent =
            (extents?.horizontalDiagonal ?: (entity.bbWidth * FOOTPRINT_DIAGONAL)).coerceAtLeast(0.6f)
        val sizeByHeight = boxHeight * MODEL_FILL_RATIO / verticalExtent
        val sizeByWidth = boxWidth * MODEL_FILL_RATIO / horizontalExtent
        val size = min(sizeByHeight, sizeByWidth).coerceIn(MODEL_MIN_SIZE, MODEL_MAX_SIZE)
        val translation = Vector3f(0.0f, renderState.boundingBoxHeight / 2.0f + 0.0625f, 0.0f)
        val rotation = Quaternionf().rotateZ(Mth.PI) //  这里Z轴转一百八，不然渲染出来是倒立的
        val cameraTilt = Quaternionf().rotateX(MODEL_PITCH * (Mth.PI / 180.0f))
        rotation.mul(cameraTilt)
        graphics.entity(renderState, size, translation, rotation, cameraTilt, x0, y0, x1, y1)
    }

    /**
     * 玩家相机相对目标身体朝向的水平角（度，归一化到 [-180,180)）。
     * 0 表示玩家正看生物正面，±180 表示看到背面。若左右转向与预期相反，把下方减号改成加号即可。
     */
    private fun relativeViewYaw(entity: LivingEntity): Float {
        val cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera.position()
        val dx = cameraPosition.x - entity.x
        val dz = cameraPosition.z - entity.z
        if (dx * dx + dz * dz < 1.0e-8) return 0.0f
        val yawToCamera = Math.toDegrees(atan2(-dx, dz)).toFloat()
        var relative = yawToCamera - entity.yBodyRot
        relative = ((relative % 360.0f) + 540.0f) % 360.0f - 180.0f
        return relative
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
