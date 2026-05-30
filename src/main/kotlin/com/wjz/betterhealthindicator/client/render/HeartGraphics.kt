package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f

/**
 * 爱心贴图与四边形绘制工具，供头顶血条与掉血粒子共用。
 *
 * 使用原版心形精灵的独立 PNG（无需访问 GUI 图集），并以双面绘制规避背面剔除。
 */
object HeartGraphics {
    val FULL: Identifier = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png")
    val HALF: Identifier = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/half.png")
    val CONTAINER: Identifier = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png")

    const val SIZE: Float = 9.0f

    /** 绘制一个带颜色（含透明度）的贴图四边形，正反两面，避免背面剔除导致不可见。 */
    fun quad(consumer: VertexConsumer, matrix: Matrix4f, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        // 正面
        vertex(consumer, matrix, left, bottom, 0.0f, 1.0f, color)
        vertex(consumer, matrix, right, bottom, 1.0f, 1.0f, color)
        vertex(consumer, matrix, right, top, 1.0f, 0.0f, color)
        vertex(consumer, matrix, left, top, 0.0f, 0.0f, color)
        // 背面（反向缠绕） 实际上不需要，因为永远是面对着玩家的
//        vertex(consumer, matrix, left, top, 0.0f, 0.0f, color)
//        vertex(consumer, matrix, right, top, 1.0f, 0.0f, color)
//        vertex(consumer, matrix, right, bottom, 1.0f, 1.0f, color)
//        vertex(consumer, matrix, left, bottom, 0.0f, 1.0f, color)
    }

    private fun vertex(consumer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, u: Float, v: Float, color: Int) {
        consumer.addVertex(matrix, x, y, 0.0f)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setNormal(0.0f, 0.0f, 1.0f)
    }
}
