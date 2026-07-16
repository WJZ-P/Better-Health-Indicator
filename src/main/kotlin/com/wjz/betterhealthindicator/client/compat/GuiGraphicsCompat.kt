package com.wjz.betterhealthindicator.client.compat

import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
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

fun BhiGuiGraphics.bhiBlitSprite(sprite: BhiIdentifier, x: Int, y: Int, width: Int, height: Int) {
    //? if >=1.21.5 {
    this.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height)
    //?} else {
    /*this.blitSprite({ texture -> net.minecraft.client.renderer.RenderType.guiTextured(texture) }, sprite, x, y, width, height)*/
    //?}
}

fun BhiGuiGraphics.bhiBlitMobEffectSprite(sprite: BhiMobEffectSprite, x: Int, y: Int, width: Int, height: Int) {
    //? if >=1.21.5 {
    this.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height)
    //?} else {
    /*this.blitSprite({ texture -> net.minecraft.client.renderer.RenderType.guiTextured(texture) }, sprite, x, y, width, height)*/
    //?}
}

fun BhiGuiGraphics.bhiBlitTexture(
    texture: BhiIdentifier,
    x: Int,
    y: Int,
    u: Float,
    v: Float,
    width: Int,
    height: Int,
    textureWidth: Int,
    textureHeight: Int,
) {
    //? if >=1.21.5 {
    this.blit(
        net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
        texture,
        x,
        y,
        u,
        v,
        width,
        height,
        textureWidth,
        textureHeight,
    )
    //?} else {
    /*this.blit(
        { resource -> net.minecraft.client.renderer.RenderType.guiTextured(resource) },
        texture,
        x,
        y,
        u,
        v,
        width,
        height,
        textureWidth,
        textureHeight,
    )*/
    //?}
}

fun BhiGuiGraphics.bhiPushPose() {
    //? if >=1.21.5 {
    this.pose().pushMatrix()
    //?} else {
    /*this.pose().pushPose()*/
    //?}
}

fun BhiGuiGraphics.bhiPopPose() {
    //? if >=1.21.5 {
    this.pose().popMatrix()
    //?} else {
    /*this.pose().popPose()*/
    //?}
}

fun BhiGuiGraphics.bhiTranslate(x: Float, y: Float) {
    //? if >=1.21.5 {
    this.pose().translate(x, y)
    //?} else {
    /*this.pose().translate(x, y, 0.0f)*/
    //?}
}

fun BhiGuiGraphics.bhiScale(x: Float, y: Float) {
    //? if >=1.21.5 {
    this.pose().scale(x, y)
    //?} else {
    /*this.pose().scale(x, y, 1.0f)*/
    //?}
}

fun BhiGuiGraphics.bhiEntity(
    entity: LivingEntity,
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
    //?} else if >=1.21.5 {
    /*this.submitEntityRenderState(state, scale, translation, rotation, cameraRotation, x0, y0, x1, y1)*/
    //?} else {
    /*net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
        this,
        (x0 + x1) / 2.0f,
        (y0 + y1) / 2.0f,
        scale,
        translation,
        rotation,
        cameraRotation,
        entity,
    )*/
    //?}
}
