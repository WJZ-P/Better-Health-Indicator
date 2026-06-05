package com.wjz.betterhealthindicator.client.hud

import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.PanelCorner
import com.wjz.betterhealthindicator.config.PanelFrameShape
import com.wjz.betterhealthindicator.client.render.EntityModelExtents
import com.wjz.betterhealthindicator.client.render.EntitySelector
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
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
import kotlin.math.sqrt

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

    // —— 面板与边框配色（统一在此调整）——
    // 面板整体背景（轻微竖向渐变，半透明）。
    private const val PANEL_BG_TOP = 0xC0121218.toInt()
    private const val PANEL_BG_BOTTOM = 0xC006060A.toInt()
    // 圆形视口内底（不透明，竖向渐变营造纵深）。
    private const val FRAME_BG_TOP = 0xFF1C1C28.toInt()
    private const val FRAME_BG_BOTTOM = 0xFF0C0C14.toInt()
    // 圆形视口边框颜色与粗细（像素）。
    private const val FRAME_BORDER_COLOR = 0xFF4A4A5C.toInt()
    private const val FRAME_BORDER_THICKNESS = 1

    // 正方形边框直接复用原版「未选中快捷栏格」贴图（26×26，拉伸缩放）。
    private val SLOT_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "gamemode_switcher/slot")
    private const val SLOT_NATIVE_SIZE = 26.0f
    private const val SLOT_NATIVE_BORDER = 2.0f // 贴图斜角边框占用的像素，用于按比例内缩模型框
    private const val BAR_BACKGROUND = 0xC0202020.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val MODEL_PITCH = -15.0f
    private const val SQRT2 = 1.41421356f

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

                graphics.fillGradient(
                    panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_BG_TOP, PANEL_BG_BOTTOM,
                )

                // 模型视口：左侧正方形区域，按形状绘制边框，并返回模型可绘制的内框。
                val frameX0 = panelX + PADDING
                val frameY0 = panelY + PADDING
                val frameSize = PANEL_HEIGHT - PADDING * 2
                val frameX1 = frameX0 + frameSize
                val frameY1 = frameY0 + frameSize

                val inner = drawModelFrame(graphics, config.panelFrameShape, frameX0, frameY0, frameX1, frameY1)
                if (config.panelShowModel) {
                    renderEntityModel(graphics, target, inner[0], inner[1], inner[2], inner[3])
                }

                val font = minecraft.font
                val textX = frameX1 + 6
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

    /**
     * 绘制模型视口边框（正方形/圆形），返回模型可绘制的内框 [x0, y0, x1, y1]。
     * 圆形采用扫描线程序绘制（无需贴图）；模型内框取内切正方形，确保模型不溢出圆外。
     */
    private fun drawModelFrame(
        graphics: GuiGraphicsExtractor,
        shape: PanelFrameShape,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): IntArray {
        val t = FRAME_BORDER_THICKNESS
        return when (shape) {
            PanelFrameShape.SQUARE -> {
                val size = x1 - x0
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x0, y0, size, y1 - y0)
                // 模型框按贴图边框比例内缩，避免模型压到斜角边框。
                val inset = (size * SLOT_NATIVE_BORDER / SLOT_NATIVE_SIZE).toInt().coerceAtLeast(2)
                intArrayOf(x0 + inset, y0 + inset, x1 - inset, y1 - inset)
            }

            PanelFrameShape.CIRCLE -> {
                val radius = (x1 - x0) / 2
                val cx = x0 + radius
                val cy = y0 + radius
                fillDisk(graphics, cx, cy, radius, FRAME_BORDER_COLOR)
                fillDiskGradient(graphics, cx, cy, radius - t, FRAME_BG_TOP, FRAME_BG_BOTTOM)
                // 内切正方形：半边长 = (半径 - 边框) / √2，模型限制其中即不会越出圆周。
                val half = ((radius - t) / SQRT2).toInt()
                intArrayOf(cx - half, cy - half, cx + half, cy + half)
            }
        }
    }

    /** 扫描线填充实心圆盘（每行一条 1px 高的水平条）。 */
    private fun fillDisk(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
        var dy = -r
        while (dy <= r) {
            val halfWidth = sqrt((r * r - dy * dy).toFloat()).toInt()
            graphics.fill(cx - halfWidth, cy + dy, cx + halfWidth, cy + dy + 1, color)
            dy++
        }
    }

    /** 扫描线填充竖向渐变圆盘。 */
    private fun fillDiskGradient(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, top: Int, bottom: Int) {
        if (r <= 0) return
        var dy = -r
        while (dy <= r) {
            val halfWidth = sqrt((r * r - dy * dy).toFloat()).toInt()
            val color = lerpColor(top, bottom, (dy + r).toFloat() / (2 * r))
            graphics.fill(cx - halfWidth, cy + dy, cx + halfWidth, cy + dy + 1, color)
            dy++
        }
    }

    /** 按比例 t∈[0,1] 在两个 ARGB 颜色间线性插值。 */
    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val s = t.coerceIn(0.0f, 1.0f)
        val a = lerpChannel(from ushr 24, to ushr 24, s)
        val r = lerpChannel((from ushr 16) and 0xFF, (to ushr 16) and 0xFF, s)
        val g = lerpChannel((from ushr 8) and 0xFF, (to ushr 8) and 0xFF, s)
        val b = lerpChannel(from and 0xFF, to and 0xFF, s)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt().coerceIn(0, 255)

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
