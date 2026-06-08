package com.wjz.betterhealthindicator.client.hud

import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.PanelCorner
import com.wjz.betterhealthindicator.config.PanelFrameShape
import com.wjz.betterhealthindicator.client.render.AttackTracker
import com.wjz.betterhealthindicator.client.render.EntityModelExtents
import com.wjz.betterhealthindicator.client.render.EntitySelector
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
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
    private const val PANEL_BG_TOP = 0xC01E1E26.toInt()
    private const val PANEL_BG_BOTTOM = 0xC0101016.toInt()
    // 圆形视口内底（不透明，竖向渐变营造纵深）。
    private const val FRAME_BG_TOP = 0xFF1C1C28.toInt()
    private const val FRAME_BG_BOTTOM = 0xFF0C0C14.toInt()
    // 圆形视口边框颜色与粗细（像素）。
    private const val FRAME_BORDER_COLOR = 0xFF4A4A5C.toInt()
    private const val FRAME_BORDER_THICKNESS = 1

    // 原版hud资产: gamemode_switcher/slot 这个是F3+F4 面板里面的未选中态,四角带缺口,gamemode_switcher/selection是选中态，金色的
    // hud/hotbar_selection是快捷栏选中态

    // private val SLOT_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "gamemode_switcher/slot")
    // private val SELECTION_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "gamemode_switcher/selection")
    // private const val SLOT_NATIVE_SIZE = 26.0f
    // private const val SLOT_NATIVE_BORDER = 2.0f // 贴图斜角边框占用的像素，用于按比例内缩模型框

    // 左上角面板HUD用F3+F4的面板的未选中态来做
    private val HOTBAR_SELECTION_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "gamemode_switcher/slot")
    private const val HOTBAR_SELECTION_NATIVE_SIZE = 22.0f
    private const val HOTBAR_SELECTION_NATIVE_BORDER = 2.0f // 选中框白边占用像素，用于按比例内缩模型框
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    // —— 面板立体边框：最外深色描边 + 内一圈外凸 bevel（顶/左高光，底/右阴影）——
    private const val PANEL_BORDER = 0xD0000000.toInt()
    private const val BEVEL_HIGHLIGHT = 0x40FFFFFF.toInt()
    private const val BEVEL_SHADOW = 0x55000000.toInt()

    // —— 血条配色：凹槽底 + 描边 + 前景顶部高光（凹槽与面板底色均较初版提亮约 10%，不至于太暗）——
    private const val BAR_TRACK = 0xD024242A.toInt()
    private const val BAR_BORDER = 0xFF000000.toInt()
    private const val BAR_GLOSS = 0x55FFFFFF.toInt() // 前景高光（以棋盘格颗粒方式打点，半覆盖故透明度略高）

    // —— 血条前景按血量三档着色（荧光亮色填充）；血量数字统一纯白，避免与填充色冲突 ——
    private const val FILL_HEALTHY = 0xFF29A33D.toInt() // >50% 荧光史莱姆绿
    private const val FILL_WARNING = 0xFFA37029.toInt() // 20%~50% 明亮金黄
    private const val FILL_DANGER = 0xFFA32929.toInt()  // <20% 刺目鲜红
    // 血量数字（当前/最大）浅灰；分隔符 "/" 用更暗的中灰以作区分。
    private const val HEALTH_NUM_TEXT = 0xFFFFFF
    private const val HEALTH_NUM_SEP = 0xAAAAAA
    private const val MODEL_PITCH = -15.0f  //  3D模型的俯视角
    private const val SQRT2 = 1.41421356f

    // 模型在渲染框中占用的比例（留少量边距，避免模型网格略超碰撞箱时贴边）。
    private const val MODEL_FILL_RATIO = 0.9f
    private const val MODEL_MIN_SIZE = 5.0f
    private const val MODEL_MAX_SIZE = 45.0f
    // √2：MC 碰撞箱水平足迹为“宽×宽”正方形，绕行到 45° 视角时横向投影最大可达 宽×√2，取此最坏值保证不被裁切。
    private const val FOOTPRINT_DIAGONAL = 1.41421356f

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
                // 优先准星目标；都没有时，最低优先级兜底显示有效期内的“最近受击生物”。
                val target = EntitySelector.pickPanelTarget(frame) ?: pickAttackedFallback(frame) ?: return@HudElement

                val panelX = when (config.panelCorner) {
                    PanelCorner.TOP_LEFT -> MARGIN
                    PanelCorner.TOP_RIGHT -> graphics.guiWidth() - PANEL_WIDTH - MARGIN
                }
                val panelY = MARGIN

                drawPanelBackground(graphics, panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT)

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
                val bold = config.panelTextBold
                val nameText =
                    if (bold) target.displayName.copy().withStyle(ChatFormatting.BOLD) else target.displayName
                graphics.text(font, nameText, textX, panelY + 6, TEXT_COLOR, true)

                // 血条：凹槽 + 描边 + 随血量红→黄→绿渐变前景 + 顶部高光，血量数值居中叠加其上。
                val healthRatio = (target.health / target.maxHealth).coerceIn(0.0f, 1.0f)
                val barX0 = textX
                val barX1 = panelX + PANEL_WIDTH - PADDING - 4
                val barY0 = panelY + 22
                val barY1 = barY0 + 13
                drawHealthBar(graphics, font, barX0, barY0, barX1, barY1, healthRatio, target.health, target.maxHealth, bold)
            },
        )
        BetterHealthIndicatorLogger.info("Health panel HUD registered.")
    }

    /**
     * 兜底目标：在有效期内（[AttackTracker]）且仍满足通用渲染条件的“最近受击生物”，否则返回 null。
     * 作为最低优先级，仅当准星没有命中可显示目标时使用。
     */
    private fun pickAttackedFallback(frame: EntitySelector.Frame): LivingEntity? {
        val attacked = AttackTracker.tracked(frame.config) ?: return null
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
            //  这里是方形的框框
            PanelFrameShape.SQUARE -> {
                val size = x1 - x0
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE, x0, y0, size, y1 - y0)
                // 模型框按贴图白边比例内缩，避免模型压到边框。
                val inset = (size * HOTBAR_SELECTION_NATIVE_BORDER / HOTBAR_SELECTION_NATIVE_SIZE).toInt().coerceAtLeast(2)
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

    /** 面板背景：半透明深色底（竖向渐变）+ 最外深色描边 + 内一圈外凸 bevel，营造原版风格立体质感。 */
    private fun drawPanelBackground(graphics: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int) {
        graphics.fillGradient(x0, y0, x1, y1, PANEL_BG_TOP, PANEL_BG_BOTTOM)
        // 注意：outline 参数是 (x, y, 宽, 高)，与 fill 的 (左, 上, 右, 下) 语义不同。
        graphics.outline(x0, y0, x1 - x0, y1 - y0, PANEL_BORDER)
        // 内一圈：顶/左高光、底/右阴影 → 外凸立体感。
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, BEVEL_HIGHLIGHT)
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, BEVEL_HIGHLIGHT)
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, BEVEL_SHADOW)
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, BEVEL_SHADOW)
    }

    /**
     * 血条：凹槽底 + 描边 + 按血量三档着色的前景 + 顶部高光，并把「当前 / 上限」数值居中叠加其上（无阴影）。
     */
    private fun drawHealthBar(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        ratio: Float,
        health: Float,
        maxHealth: Float,
        bold: Boolean,
    ) {
        graphics.fill(x0, y0, x1, y1, BAR_TRACK)
        // outline 用 (x, y, 宽, 高)：在血条外扩 1px 描边。
        graphics.outline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, BAR_BORDER)
        // 前景填充按血量三档用荧光亮色当亮底；数字统一纯白，避免与填充色冲突。
        val fillColor = when {
            ratio > 0.5f -> FILL_HEALTHY
            ratio >= 0.2f -> FILL_WARNING
            else -> FILL_DANGER
        }
        val fillX1 = x0 + ((x1 - x0) * ratio).toInt()
        if (fillX1 > x0) {
            graphics.fill(x0, y0, fillX1, y1, fillColor)
            // 前景上半部以棋盘格（间隔 1px）打半透明白高光，营造 MC 像素颗粒质感。
            drawGlossCheckerboard(graphics, x0, y0, fillX1, y1)
        }
        // 数值：当前/最大血量浅灰，分隔符用更暗的中灰以作区分；无阴影；整体粗细跟随面板「文本加粗」设置。
        val text = Component.empty()
            .append(Component.literal(ceil(health).toInt().toString()).withColor(HEALTH_NUM_TEXT))
            .append(Component.literal(" / ").withColor(HEALTH_NUM_SEP))
            .append(Component.literal(ceil(maxHealth).toInt().toString()).withColor(HEALTH_NUM_TEXT))
            .apply { if (bold) withStyle(ChatFormatting.BOLD) }
        val cx = (x0 + x1) / 2
        val cy = y0 + (y1 - y0 - font.lineHeight) / 2 + 1
        graphics.text(font, text, cx - font.width(text) / 2, cy, TEXT_COLOR, true)
    }

    /**
     * 在矩形区域内以「棋盘格」方式（间隔 1px）打半透明白高光点，营造 Minecraft 像素颗粒质感。
     * 仅在 (x-x0)+(y-y0) 为偶数的格子着色，奇偶交错形成棋盘。
     */
    private fun drawGlossCheckerboard(graphics: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int) {
        var py = y0
        while (py < y1) {
            var px = x0
            while (px < x1) {
                if (((px - x0) + (py - y0)) and 1 == 0) {
                    graphics.fill(px, py, px + 1, py + 1, BAR_GLOSS)
                }
                px++
            }
            py++
        }
    }
}
