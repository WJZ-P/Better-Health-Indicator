package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
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

    const val SIZE: Float = 9.0f

    // —— GUI（2D HUD）用心形精灵 id：走 GUI 图集（无 textures/gui/sprites 前缀、无 .png），供屏幕面板心形血条复用。 ——
    // container_blinking 即原版受击/回血时的白色外圈高亮。
    val GUI_CONTAINER: Identifier = Identifier.withDefaultNamespace("hud/heart/container")
    val GUI_CONTAINER_BLINKING: Identifier = Identifier.withDefaultNamespace("hud/heart/container_blinking")

    /**
     * 分层爱心颜色，全部取自原版自带的异色心形精灵（无需染色/额外资源）。
     * 顺序即叠层顺序：每满一排(20HP)进入下一层颜色，超出后循环并以 xN 文字标注。
     */
    enum class HeartTier(fullName: String, halfName: String) {
        RED("full", "half"),
        GOLD("absorbing_full", "absorbing_half"),
        GREEN("poisoned_full", "poisoned_half"),
        BLUE("frozen_full", "frozen_half"),
        DARK("withered_full", "withered_half"),
        ;

        val full: Identifier = heartSprite(fullName)
        val half: Identifier = heartSprite(halfName)

        // GUI 图集版（供 2D 面板心形血条用）。
        val guiFull: Identifier = Identifier.withDefaultNamespace("hud/heart/$fullName")
        val guiHalf: Identifier = Identifier.withDefaultNamespace("hud/heart/$halfName")

        companion object {
            /** 按叠层索引取颜色，超出调色板则循环。 */
            fun byLayer(layer: Int): HeartTier {
                val values = entries
                return values[((layer % values.size) + values.size) % values.size]
            }
        }
    }

    /** 所有半心贴图集合，用于判断是否需要按掉血方向翻转 U（决定填充哪半边）。 */
    private val HALF_TEXTURES: Set<Identifier> = HeartTier.entries.map { it.half }.toSet()

    fun isHalfTexture(texture: Identifier): Boolean = texture in HALF_TEXTURES

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
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setNormal(0.0f, 0.0f, 1.0f)
    }
}
