package com.wjz.betterhealthindicator.client.compat

import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import org.joml.Quaternionf
import org.joml.Vector3f

//? if >=26.1 {
typealias BhiGuiGraphics = net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*typealias BhiGuiGraphics = net.minecraft.client.gui.GuiGraphics*/
//?}

fun BhiGuiGraphics.bhiText(font: Font, text: Component, x: Int, y: Int, color: Int, shadow: Boolean) {
    //? if >=26.1 {
    this.text(font, text, x, y, color, shadow)
    //?} else {
    /*this.drawString(font, text, x, y, color, shadow)*/
    //?}
}

fun BhiGuiGraphics.bhiOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {
    //? if >=26.1 {
    this.outline(x, y, width, height, color)
    //?} else {
    /*this.renderOutline(x, y, width, height, color)*/
    //?}
}

fun BhiGuiGraphics.bhiEntity(
    state: EntityRenderState,
    scale: Float,
    translation: Vector3f,
    rotation: Quaternionf,
    cameraRotation: Quaternionf,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
) {
    //? if >=26.1 {
    this.entity(state, scale, translation, rotation, cameraRotation, x0, y0, x1, y1)
    //?} else {
    /*this.submitEntityRenderState(state, scale, translation, rotation, cameraRotation, x0, y0, x1, y1)*/
    //?}
}
