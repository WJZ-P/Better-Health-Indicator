package com.wjz.betterhealthindicator.client.hud

import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.PanelCorner
import com.wjz.betterhealthindicator.config.PanelBarStyle
import com.wjz.betterhealthindicator.config.PanelFrameShape
import com.wjz.betterhealthindicator.config.PanelTheme
import com.wjz.betterhealthindicator.client.render.AttackTracker
import com.wjz.betterhealthindicator.client.render.EntityModelExtents
import com.wjz.betterhealthindicator.client.render.EntitySelector
import com.wjz.betterhealthindicator.client.render.HeartBlinkTracker
import com.wjz.betterhealthindicator.client.render.HeartGraphics
import com.wjz.betterhealthindicator.client.render.HeartLayout
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 屏幕角落血量面板：框 + 关注生物的实时 3D 模型 + 名字与血量信息。
 *
 * 通过 Fabric [HudElementRegistry] 注册，在 HUD 提取阶段（[HudElement.extractRenderState]）绘制，
 * 实体模型复用原版 [InventoryScreen.extractEntityInInventoryFollowsMouse]。
 */
object HealthPanelHud {
    private const val PANEL_WIDTH_DEFAULT = 120 // 面板默认/最小宽度
    private const val PANEL_WIDTH_MAX = 220      // 名字过长时最多扩展到的宽度，超出则省略
    private const val CONTENT_RIGHT_PAD = 6      // 文本/血条距面板右沿的内边距
    private const val PANEL_HEIGHT = 44
    private const val MARGIN = 2
    private const val PADDING = 2
    private const val BAR_HEIGHT = 12 // 血条高度（像素），调小更显精致协调

    // —— 心形血条（复用 HeartLayout 布局；走 GUI 图集 sprite） ——
    private const val HEART_SIZE = 9 // 单颗心贴图边长（像素，原版尺寸）
    private const val HEART_STEP = 8 // 相邻心横向步距（略叠，原版一致）

    // 圆形视口边框粗细（像素）；各处配色统一见 [Theme]（深/浅两套主题）。
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
    private const val GLOSS_CELL = 2 // 棋盘格高光单元边长（像素），2x2 颗粒

    // —— 血条前景按血量三档着色（荧光亮色填充，与主题无关）——
    private const val FILL_HEALTHY = 0xFF33CC4C.toInt() // >50% 荧光史莱姆绿
    private const val FILL_WARNING = 0xFFCC8C33.toInt() // 20%~50% 明亮金黄
    private const val FILL_DANGER = 0xFFCC3333.toInt()  // <20% 刺目鲜红

    // 血条数字配色（深浅主题共用）：白字 + 灰分隔符，统一带阴影，压在荧光填充上都清晰。
    private const val NAME_COLOR = 0xFFFFFFFF.toInt()
    private const val HEALTH_NUM_TEXT = 0xFFFFFFFF.toInt()
    private const val HEALTH_NUM_SEP = 0xFFAAAAAA.toInt()
    private const val HEALTH_NUM_SCALE = 0.8f // 血条数字缩放（<1 即比原版字体小一号）

    /**
     * 面板配色主题。深/浅两套，全部颜色集中在此一处切换：
     * - 深色：半透明深色玻璃底，浅字 + 黑阴影；
     * - 浅色：原版米白底，深字、无阴影。
     */
    private class Theme(
        val bgTop: Int,
        val bgBottom: Int,
        val border: Int,
        val bevelHighlight: Int,
        val bevelShadow: Int,
        val frameBgTop: Int,
        val frameBgBottom: Int,
        val frameBorder: Int,
        val barTrack: Int,
        val barBorder: Int,
        val barInsetShadow: Int,
        val barInsetHighlight: Int,
        val barGloss: Int,
    )

    // 深色：半透明深色玻璃（外凸 bevel：顶/左白高光、底/右黑阴影），浅字 + 黑阴影。
    private val DARK_THEME = Theme(
        bgTop = (PANEL_ALPHA shl 24) or 0x1E1E26,
        bgBottom = (PANEL_ALPHA shl 24) or 0x101016,
        border = 0xD0000000.toInt(),
        bevelHighlight = 0x40FFFFFF.toInt(),
        bevelShadow = 0x55000000.toInt(),
        frameBgTop = 0xFF1C1C28.toInt(),
        frameBgBottom = 0xFF0C0C14.toInt(),
        frameBorder = 0xFF4A4A5C.toInt(),
        barTrack = 0xD024242A.toInt(),
        barBorder = 0xFF000000.toInt(),
        barInsetShadow = 0x60000000.toInt(),
        barInsetHighlight = 0x50FFFFFF.toInt(),
        barGloss = 0x10FFFFFF.toInt(),
    )

    // 面板背景透明度（0x00 全透 ~ 0xFF 不透）：深浅两套主题共用，调低可让游戏背景透出，方便按喜好随手微调。
    private const val PANEL_ALPHA = 0xA8

    // 浅色：贴合左侧原版 slot 方形框的中性灰立体风（面板用原版容器灰系，圆形视口模仿 slot 的浅边 + 深内底）。
    private val LIGHT_THEME = Theme(
        bgTop = (PANEL_ALPHA shl 24) or 0xB2B2B2,
        bgBottom = (PANEL_ALPHA shl 24) or 0x969696,
        border = 0xFF373737.toInt(),
        bevelHighlight = 0xC0FFFFFF.toInt(),
        bevelShadow = 0x60303030.toInt(),
        frameBgTop = 0xFF5C5C5C.toInt(),
        frameBgBottom = 0xFF3C3C3C.toInt(),
        frameBorder = 0xFFAEAEAE.toInt(),
        barTrack = 0xE06E6E6E.toInt(),
        barBorder = 0xFF373737.toInt(),
        barInsetShadow = 0x60000000.toInt(),
        barInsetHighlight = 0x60FFFFFF.toInt(),
        barGloss = 0x10FFFFFF.toInt(),
    )
    private const val MODEL_PITCH = -15.0f  //  3D模型的俯视角
    private const val SQRT2 = 1.41421356f

    // 模型在渲染框中占用的比例（留少量边距，避免模型网格略超碰撞箱时贴边）。
    private const val MODEL_FILL_RATIO = 0.8f
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

                val font = minecraft.font
                val bold = config.panelTextBold
                val frameSize = PANEL_HEIGHT - PADDING * 2
                // 文本区左沿相对面板左沿的偏移：内边距 + 模型框 + 间隙。
                val contentLeft = PADDING + frameSize + 6
                // 面板宽度随名字完整宽度在「默认~最大」间自适应；超出最大宽度的名字会被省略。
                val fullNameWidth =
                    font.width(if (bold) target.displayName.copy().withStyle(ChatFormatting.BOLD) else target.displayName)
                // 心形样式：预先算好布局，使面板宽度能容纳整排爱心。
                val heartsView = if (config.panelBarStyle == PanelBarStyle.HEARTS)
                    HeartLayout.compute(target.health, target.maxHealth, config) else null
                val nameNeed = contentLeft + fullNameWidth + CONTENT_RIGHT_PAD
                val panelWidth = if (heartsView != null) {
                    // 心形：宽度完全由内容（名字 / 整排爱心，含多层异色）决定，不设最小宽度，仅受最大宽度限制。
                    max(nameNeed, contentLeft + heartsWidth(heartsView, font) + CONTENT_RIGHT_PAD)
                        .coerceAtMost(PANEL_WIDTH_MAX)
                } else {
                    nameNeed.coerceIn(PANEL_WIDTH_DEFAULT, PANEL_WIDTH_MAX)
                }
                val maxNameWidth = panelWidth - contentLeft - CONTENT_RIGHT_PAD
                val nameText = fitName(font, target.displayName, bold, maxNameWidth)

                val panelX = when (config.panelCorner) {
                    PanelCorner.TOP_LEFT -> MARGIN
                    PanelCorner.TOP_RIGHT -> graphics.guiWidth() - panelWidth - MARGIN
                }
                val panelY = MARGIN

                val theme = if (config.panelTheme == PanelTheme.LIGHT) LIGHT_THEME else DARK_THEME
                drawPanelBackground(graphics, theme, panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT)

                // 模型视口：左侧正方形区域，按形状绘制边框，并返回模型可绘制的内框。
                val frameX0 = panelX + PADDING
                val frameY0 = panelY + PADDING
                val frameX1 = frameX0 + frameSize
                val frameY1 = frameY0 + frameSize

                val inner = drawModelFrame(graphics, theme, config.panelFrameShape, frameX0, frameY0, frameX1, frameY1)
                if (config.panelShowModel) {
                    renderEntityModel(graphics, target, inner[0], inner[1], inner[2], inner[3])
                }

                val textX = frameX1 + 6
                graphics.text(font, nameText, textX, panelY + 6, NAME_COLOR, true)

                val barX0 = textX
                val barX1 = panelX + panelWidth - PADDING - 4
                val barY0 = panelY + 22
                val barY1 = barY0 + BAR_HEIGHT
                if (heartsView != null) {
                    // 心形血条：血量变化时心容器外圈闪白（受击/回血反馈，可配置关闭）。
                    val blinking = config.panelHeartHighlight && HeartBlinkTracker.update(target.id, target.health)
                    drawHearts(graphics, font, barX0, (barY0 + barY1) / 2, heartsView, blinking, !config.drainFromRight)
                } else {
                    // 纯色血条：凹槽 + 描边 + 随血量红→黄→绿渐变前景 + 顶部高光，血量数值居中叠加其上。
                    val healthRatio = (target.health / target.maxHealth).coerceIn(0.0f, 1.0f)
                    drawHealthBar(graphics, theme, font, barX0, barY0, barX1, barY1, healthRatio, target.health, target.maxHealth, bold)
                }
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

    /**
     * 名字按 [maxWidth] 自适应：放得下则原样（按需加粗），否则尾部用 "..." 省略到刚好放下。
     * 截断会丢弃原名字的富文本样式，仅保留纯文本（统一以 [NAME_COLOR] 白字渲染），命名牌场景足够。
     */
    private fun fitName(
        font: net.minecraft.client.gui.Font,
        base: Component,
        bold: Boolean,
        maxWidth: Int,
    ): Component {
        fun styled(s: String): Component =
            Component.literal(s).apply { if (bold) withStyle(ChatFormatting.BOLD) }
        val full = if (bold) base.copy().withStyle(ChatFormatting.BOLD) else base
        if (maxWidth <= 0 || font.width(full) <= maxWidth) return full
        val raw = base.string
        val ellipsis = "..."
        var len = raw.length - 1
        while (len > 0) {
            val candidate = styled(raw.substring(0, len) + ellipsis)
            if (font.width(candidate) <= maxWidth) return candidate
            len--
        }
        return styled(ellipsis)
    }

    /** 心形血条所需像素宽度（整排爱心 + 可能的 xN 倍数标注），用于面板宽度自适应。 */
    private fun heartsWidth(view: HeartLayout.View, font: net.minecraft.client.gui.Font): Int {
        var w = view.slots.size * HEART_STEP + (HEART_SIZE - HEART_STEP)
        if (view.multiplier > 0) w += 2 + font.width("× ${view.multiplier}")
        return w
    }

    /**
     * 心形血条：复用 [HeartLayout] 的槽位（含分层异色 / 半心 / 掉血方向），在 2D GUI 用原版心形 sprite 绘制。
     * [blinking] 为 true 时心容器外圈用 container_blinking（白圈），还原原版受击/回血高亮。
     */
    private fun drawHearts(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        x0: Int,
        centerY: Int,
        view: HeartLayout.View,
        blinking: Boolean,
        mirrorHalf: Boolean,
    ) {
        val top = centerY - HEART_SIZE / 2
        val container = if (blinking) HeartGraphics.GUI_CONTAINER_BLINKING else HeartGraphics.GUI_CONTAINER
        // HeartLayout 的 cx 是给 3D billboard 用的（渲染带 scale(-x) 镜像，屏幕左对应大 cx）。
        // 2D 面板无该镜像，故按 cx 降序还原同样的屏幕左→右视觉顺序（含掉血方向）。
        val ordered = view.slots.sortedByDescending { it.cx }
        ordered.forEachIndexed { i, slot ->
            val x = x0 + i * HEART_STEP
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, container, x, top, HEART_SIZE, HEART_SIZE)
            // 分层时空出的顶层揭示下一层满心作底，而非黑底。
            slot.baseTier?.let {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, it.guiFull, x, top, HEART_SIZE, HEART_SIZE)
            }
            when (slot.top) {
                HeartLayout.Top.FULL ->
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, slot.topTier.guiFull, x, top, HEART_SIZE, HEART_SIZE)
                // 半心填充侧跟随掉血方向：从右往左扣→左半（原版默认 sprite）；从左往右扣→水平镜像成右半。
                HeartLayout.Top.HALF -> if (mirrorHalf) {
                    val pose = graphics.pose()
                    pose.pushMatrix()
                    pose.translate((2 * x + HEART_SIZE).toFloat(), 0.0f)
                    pose.scale(-1.0f, 1.0f)
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, slot.topTier.guiHalf, x, top, HEART_SIZE, HEART_SIZE)
                    pose.popMatrix()
                } else {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, slot.topTier.guiHalf, x, top, HEART_SIZE, HEART_SIZE)
                }
                HeartLayout.Top.NONE -> {}
            }
        }
        if (view.multiplier > 0) {
            val label = Component.literal("× ${view.multiplier}")
            // 与头顶一致：按倍数分档着色（补足不透明 alpha）。
            val color = 0xFF000000.toInt() or HeartLayout.multiplierColor(view.multiplier)
            graphics.text(font, label, x0 + ordered.size * HEART_STEP + 2, centerY - font.lineHeight / 2, color, true)
        }
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
        theme: Theme,
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
                fillDisk(graphics, cx, cy, radius, theme.frameBorder)
                fillDiskGradient(graphics, cx, cy, radius - t, theme.frameBgTop, theme.frameBgBottom)
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
    private fun drawPanelBackground(graphics: GuiGraphicsExtractor, theme: Theme, x0: Int, y0: Int, x1: Int, y1: Int) {
        graphics.fillGradient(x0, y0, x1, y1, theme.bgTop, theme.bgBottom)
        // 注意：outline 参数是 (x, y, 宽, 高)，与 fill 的 (左, 上, 右, 下) 语义不同。
        graphics.outline(x0, y0, x1 - x0, y1 - y0, theme.border)
        // 内一圈：顶/左高光、底/右阴影 → 外凸立体感。
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, theme.bevelHighlight)
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, theme.bevelHighlight)
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, theme.bevelShadow)
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, theme.bevelShadow)
    }

    /**
     * 血条：凹槽底 + 描边 + 按血量三档着色的前景 + 顶部高光，并把「当前 / 上限」数值居中叠加其上（无阴影）。
     */
    private fun drawHealthBar(
        graphics: GuiGraphicsExtractor,
        theme: Theme,
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
        graphics.fill(x0, y0, x1, y1, theme.barTrack)
        // outline 用 (x, y, 宽, 高)：在血条外扩 1px 描边。
        graphics.outline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, theme.barBorder)
        // 前景填充按血量三档用荧光亮色当亮底；数字统一纯白，避免与填充色冲突。
        val fillColor = when {
            ratio > 0.5f -> FILL_HEALTHY
            ratio >= 0.2f -> FILL_WARNING
            else -> FILL_DANGER
        }
        val fillX1 = x0 + ((x1 - x0) * ratio).toInt()
        if (fillX1 > x0) {
            graphics.fill(x0, y0, fillX1, y1, fillColor)
            // 前景上半部以棋盘格（2x2）打半透明白高光，营造 MC 像素颗粒质感。
            drawGlossCheckerboard(graphics, theme, x0, y0, fillX1, y1)
        }
        // 内沿 1px 凹陷描边（叠在内容之上）：顶/左阴影 + 底/右高光，使血条像嵌入面板的凹槽。
        drawInsetBevel(graphics, theme, x0, y0, x1, y1)
        // 数值：当前/最大血量随主题取色（深色浅字/浅色深字），分隔符用区分灰；阴影随主题；粗细跟随面板「文本加粗」设置。
        val text = Component.empty()
            .append(Component.literal(ceil(health).toInt().toString()).withColor(HEALTH_NUM_TEXT))
            .append(Component.literal(" / ").withColor(HEALTH_NUM_SEP))
            .append(Component.literal(ceil(maxHealth).toInt().toString()).withColor(HEALTH_NUM_TEXT))
            .apply { if (bold) withStyle(ChatFormatting.BOLD) }
        // 数字缩小一号：用 2D 矩阵以血条中心为锚点缩放后绘制（MC 字体无原生小字号）。
        val s = HEALTH_NUM_SCALE
        val tw = font.width(text)
        val centerX = (x0 + x1) / 2f
        val centerY = (y0 + y1) / 2f
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(centerX - tw * s / 2f, centerY - font.lineHeight * s / 2f)
        pose.scale(s, s)
        graphics.text(font, text, 0, 0, HEALTH_NUM_TEXT, true)
        pose.popMatrix()
    }

    /**
     * 在矩形区域内以「棋盘格」方式打半透明白高光块，营造 Minecraft 像素颗粒质感。
     * 每格 [GLOSS_CELL]×[GLOSS_CELL] 像素，按格行列号奇偶交错着色；边缘格用 min 裁剪不越界。
     */
    private fun drawGlossCheckerboard(graphics: GuiGraphicsExtractor, theme: Theme, x0: Int, y0: Int, x1: Int, y1: Int) {
        var row = 0
        var py = y0
        while (py < y1) {
            var col = 0
            var px = x0
            while (px < x1) {
                if ((row + col) and 1 == 0) {
                    graphics.fill(px, py, min(px + GLOSS_CELL, x1), min(py + GLOSS_CELL, y1), theme.barGloss)
                }
                px += GLOSS_CELL
                col++
            }
            py += GLOSS_CELL
            row++
        }
    }

    /**
     * 在矩形 [x0,y0,x1,y1) 的最内沿绘制 1px 凹陷 bevel：顶/左阴影、底/右高光（光自左上），
     * 叠在血条内容之上，使其呈现「嵌入面板」的凹陷立体感。若想改为凸出，把阴影与高光对调即可。
     */
    private fun drawInsetBevel(graphics: GuiGraphicsExtractor, theme: Theme, x0: Int, y0: Int, x1: Int, y1: Int) {
        graphics.fill(x0, y0, x1, y0 + 1, theme.barInsetShadow)       // 顶
        graphics.fill(x0, y0, x0 + 1, y1, theme.barInsetShadow)       // 左
        graphics.fill(x0, y1 - 1, x1, y1, theme.barInsetHighlight)    // 底
        graphics.fill(x1 - 1, y0, x1, y1, theme.barInsetHighlight)    // 右
    }
}
