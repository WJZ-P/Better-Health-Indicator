package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import com.wjz.betterhealthindicator.client.compat.MinecraftCompat
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier as Identifier
import net.minecraft.client.renderer.texture.OverlayTexture
import org.joml.Matrix4f
import kotlin.math.cos
import kotlin.math.sin

/** 心形精灵路径构造。提为顶层函数，避免枚举条目反向依赖 [HeartGraphics] 对象造成循环初始化。 */
private fun heartSprite(name: String): Identifier =
    Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/$name.png")

/**
 * 爱心贴图与四边形绘制工具，供头顶血条与掉血粒子共用。
 *
 * 使用原版心形精灵的独立 PNG（无需访问 GUI 图集），单面绘制（爱心永远朝向玩家）。
 */
object HeartGraphics {
    val FULL: Identifier = heartSprite("full")
    val HALF: Identifier = heartSprite("half")
    val CONTAINER: Identifier = heartSprite("container")

    // 受击/回血高亮：心容器外圈闪白（原版同款），头顶 3D 血条用。
    val CONTAINER_BLINKING: Identifier = heartSprite("container_blinking")

    // 极限（hardcore）模式容器：强力怪物仅剩最后一排血条时启用，外观更硬核。
    val CONTAINER_HARDCORE: Identifier = heartSprite("container_hardcore")
    val CONTAINER_HARDCORE_BLINKING: Identifier = heartSprite("container_hardcore_blinking")

    const val SIZE: Float = 9.0f

    // —— GUI（2D HUD）用心形精灵 id：走 GUI 图集（无 textures/gui/sprites 前缀、无 .png），供屏幕面板心形血条复用。 ——
    // container_blinking 即原版受击/回血时的白色外圈高亮。
    val GUI_CONTAINER: Identifier = Identifier.withDefaultNamespace("hud/heart/container")
    val GUI_CONTAINER_BLINKING: Identifier = Identifier.withDefaultNamespace("hud/heart/container_blinking")
    val GUI_CONTAINER_HARDCORE: Identifier = Identifier.withDefaultNamespace("hud/heart/container_hardcore")
    val GUI_CONTAINER_HARDCORE_BLINKING: Identifier = Identifier.withDefaultNamespace("hud/heart/container_hardcore_blinking")

    /** 按是否极限模式选择心容器 GUI sprite（含受击闪白态）。 */
    fun guiContainer(hardcore: Boolean, blinking: Boolean): Identifier = when {
        hardcore && blinking -> GUI_CONTAINER_HARDCORE_BLINKING
        hardcore -> GUI_CONTAINER_HARDCORE
        blinking -> GUI_CONTAINER_BLINKING
        else -> GUI_CONTAINER
    }

    /** 按是否极限模式选择心容器世界 sprite（头顶 3D 血条用，含受击闪白态）。 */
    fun container(hardcore: Boolean, blinking: Boolean): Identifier = when {
        hardcore && blinking -> CONTAINER_HARDCORE_BLINKING
        hardcore -> CONTAINER_HARDCORE
        blinking -> CONTAINER_BLINKING
        else -> CONTAINER
    }

    /**
     * 2D 面板上一颗心要用的贴图来源：原版心走 GUI 图集（[Sprite] → blitSprite），
     * 染色心是运行时生成的独立 [DynamicTexture]（[Texture] → blit，非图集）。
     */
    sealed interface GuiHeart {
        /** GUI 图集精灵（原版心），用 blitSprite 绘制。 */
        class Sprite(val sprite: Identifier) : GuiHeart

        /** 独立贴图（染色心），用 blit 绘制；[size] 为贴图原始像素边长。 */
        class Texture(val texture: Identifier, val size: Int) : GuiHeart
    }

    /**
     * 一层爱心的贴图来源。
     * - [VanillaHeartTier]：最底层(layer 0)与单层模式用的原版红心（含极限 hardcore 变体）；
     * - [TintedHeartTier]：多重血条上层，按主色由灰度模板运行时染色而成。
     */
    sealed interface HeartTier {
        /** 满心（世界 sprite，头顶 3D 用）。 */
        fun fullFor(hardcore: Boolean): Identifier

        /** 半心（世界 sprite，头顶 3D 用）。 */
        fun halfFor(hardcore: Boolean): Identifier

        /** 满心（2D 面板用）。 */
        fun guiFullFor(hardcore: Boolean): GuiHeart

        /** 半心（2D 面板用）。 */
        fun guiHalfFor(hardcore: Boolean): GuiHeart

        companion object {
            /**
             * 按绝对叠层索引取该层贴图来源：layer 0（最底层/最后一排）恒为原版红心；
             * layer >= 1（多重血条上层）按配置调色板循环取色，由灰度模板染色生成。
             */
            fun byLayer(layer: Int): HeartTier {
                if (layer <= 0) return VanillaHeartTier
                val palette = com.wjz.betterhealthindicator.config.ConfigManager.config.tierColors
                if (palette.isEmpty()) return VanillaHeartTier
                return TintedHeartTier(palette[(layer - 1) % palette.size])
            }
        }
    }

    /** 原版红心（含极限 hardcore 变体）：仅最底层(layer 0)与非分层/单层模式使用。 */
    object VanillaHeartTier : HeartTier {
        private val full = heartSprite("full")
        private val half = heartSprite("half")
        private val hardcoreFull = heartSprite("hardcore_full")
        private val hardcoreHalf = heartSprite("hardcore_half")
        private val guiFull = Identifier.withDefaultNamespace("hud/heart/full")
        private val guiHalf = Identifier.withDefaultNamespace("hud/heart/half")
        private val guiHardcoreFull = Identifier.withDefaultNamespace("hud/heart/hardcore_full")
        private val guiHardcoreHalf = Identifier.withDefaultNamespace("hud/heart/hardcore_half")

        override fun fullFor(hardcore: Boolean): Identifier = if (hardcore) hardcoreFull else full
        override fun halfFor(hardcore: Boolean): Identifier = if (hardcore) hardcoreHalf else half
        override fun guiFullFor(hardcore: Boolean): GuiHeart = GuiHeart.Sprite(if (hardcore) guiHardcoreFull else guiFull)
        override fun guiHalfFor(hardcore: Boolean): GuiHeart = GuiHeart.Sprite(if (hardcore) guiHardcoreHalf else guiHalf)
    }

    /**
     * 多重血条上层染色心：贴图按主色由灰度模板运行时烘焙（见 [TintedHeartTextures]）。
     * 染色心不参与极限模式（hardcore 仅作用于最底层原版心），故 hardcore 参数被忽略。
     */
    class TintedHeartTier(private val colorRgb: Int) : HeartTier {
        override fun fullFor(hardcore: Boolean): Identifier = TintedHeartTextures.fullTexture(colorRgb)
        override fun halfFor(hardcore: Boolean): Identifier = TintedHeartTextures.halfTexture(colorRgb)
        override fun guiFullFor(hardcore: Boolean): GuiHeart = GuiHeart.Texture(TintedHeartTextures.fullTexture(colorRgb), SIZE.toInt())
        override fun guiHalfFor(hardcore: Boolean): GuiHeart = GuiHeart.Texture(TintedHeartTextures.halfTexture(colorRgb), SIZE.toInt())
    }

    /**
     * 绘制一个带颜色（含透明度）的贴图四边形（单面，因永远朝向玩家）。
     * @param flipU 为 true 时水平翻转贴图 U 坐标，用于让半心的填充侧跟随掉血方向。
     */
    fun quad(
        consumer: VertexConsumer,
        matrix: Matrix4f,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
        flipU: Boolean = false,
        z: Float = 0.0f,
    ) {
        val uLeft = if (flipU) 1.0f else 0.0f
        val uRight = if (flipU) 0.0f else 1.0f
        vertex(consumer, matrix, left, bottom, uLeft, 1.0f, color, z)
        vertex(consumer, matrix, right, bottom, uRight, 1.0f, color, z)
        vertex(consumer, matrix, right, top, uRight, 0.0f, color, z)
        vertex(consumer, matrix, left, top, uLeft, 0.0f, color, z)
    }

    /**
     * 绘制一个绕自身中心旋转 [rot] 弧度的贴图四边形（单面）。
     * 用于头顶爱心受击时，让整颗心以中心为轴整体往左/右偏转一定角度。
     * @param cx,cy 心中心坐标；@param half 半边长（贴图尺寸的一半）。
     */
    fun quadRotated(
        consumer: VertexConsumer,
        matrix: Matrix4f,
        cx: Float,
        cy: Float,
        half: Float,
        color: Int,
        rot: Float,
        flipU: Boolean = false,
        z: Float = 0.0f,
    ) {
        val uLeft = if (flipU) 1.0f else 0.0f
        val uRight = if (flipU) 0.0f else 1.0f
        val cosR = cos(rot)
        val sinR = sin(rot)
        fun corner(dx: Float, dy: Float, u: Float, v: Float) {
            val rx = dx * cosR - dy * sinR
            val ry = dx * sinR + dy * cosR
            vertex(consumer, matrix, cx + rx, cy + ry, u, v, color, z)
        }
        corner(-half, half, uLeft, 1.0f)
        corner(half, half, uRight, 1.0f)
        corner(half, -half, uRight, 0.0f)
        corner(-half, -half, uLeft, 0.0f)
    }

    /**
     * 绘制贴图的一块「子矩形」（用于碎裂碎片：每片取心形贴图的一格 UV）。
     * @param u0,v0 子矩形左上 UV；@param u1,v1 右下 UV。
     */
    fun quadUv(
        consumer: VertexConsumer,
        matrix: Matrix4f,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: Int,
        z: Float = 0.0f,
    ) {
        vertex(consumer, matrix, left, bottom, u0, v1, color, z)
        vertex(consumer, matrix, right, bottom, u1, v1, color, z)
        vertex(consumer, matrix, right, top, u1, v0, color, z)
        vertex(consumer, matrix, left, top, u0, v0, color, z)
    }

    private fun vertex(consumer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, u: Float, v: Float, color: Int, z: Float) {
        consumer.addVertex(matrix, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(MinecraftCompat.fullBright())
            .setNormal(0.0f, 0.0f, 1.0f)
    }
}
