package com.wjz.betterhealthindicator.client.compat

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.Font
import net.minecraft.util.FormattedCharSequence

//? if >=26.1 {
typealias BhiWorldRenderContext = net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
//?} else if >=1.21.9 {
/*typealias BhiWorldRenderContext = net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext*/
//?} else {
/*typealias BhiWorldRenderContext = net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext*/
//?}

// 1.21.9 起世界渲染改为提交节点；1.21.8 及更早版本直接写入顶点缓冲。
//? if >=1.21.9 {
typealias BhiWorldCollector = net.minecraft.client.renderer.SubmitNodeCollector
typealias BhiRenderType = net.minecraft.client.renderer.rendertype.RenderType
//?} else {
/*typealias BhiWorldCollector = net.minecraft.client.renderer.MultiBufferSource
typealias BhiRenderType = net.minecraft.client.renderer.RenderType*/
//?}

fun bhiEntityCutout(texture: BhiIdentifier): BhiRenderType {
    //? if >=1.21.9 {
    return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture)
    //?} else {
    /*return net.minecraft.client.renderer.RenderType.entityCutout(texture)*/
    //?}
}

fun bhiEntityTranslucent(texture: BhiIdentifier): BhiRenderType {
    //? if >=1.21.9 {
    return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture)
    //?} else {
    /*return net.minecraft.client.renderer.RenderType.entityTranslucent(texture)*/
    //?}
}

fun BhiWorldCollector.bhiSubmitGeometry(
    poseStack: PoseStack,
    renderType: BhiRenderType,
    renderer: (PoseStack.Pose, VertexConsumer) -> Unit,
) {
    //? if >=1.21.9 {
    this.submitCustomGeometry(poseStack, renderType) { pose, consumer -> renderer(pose, consumer) }
    //?} else {
    /*renderer(poseStack.last(), this.getBuffer(renderType))*/
    //?}
}

fun BhiWorldCollector.bhiSubmitText(
    font: Font,
    poseStack: PoseStack,
    x: Float,
    y: Float,
    text: FormattedCharSequence,
    shadow: Boolean,
    displayMode: Font.DisplayMode,
    light: Int,
    color: Int,
    backgroundColor: Int,
    outlineColor: Int,
) {
    //? if >=1.21.9 {
    this.submitText(poseStack, x, y, text, shadow, displayMode, light, color, backgroundColor, outlineColor)
    //?} else {
    /*font.drawInBatch(text, x, y, color, shadow, poseStack.last().pose(), this, displayMode, backgroundColor, light)*/
    //?}
}
