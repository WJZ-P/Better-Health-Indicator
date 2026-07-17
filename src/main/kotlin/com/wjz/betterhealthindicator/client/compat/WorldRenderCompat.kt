package com.wjz.betterhealthindicator.client.compat

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component

//? if >=1.16 {
typealias BhiFormattedText = net.minecraft.util.FormattedCharSequence
//?} else {
/*typealias BhiFormattedText = Component*/
//?}

enum class BhiFontDisplayMode {
    NORMAL,
    SEE_THROUGH,
    POLYGON_OFFSET,
}

//? if >=1.17 {
private fun BhiFontDisplayMode.vanilla(): Font.DisplayMode = when (this) {
    BhiFontDisplayMode.NORMAL -> Font.DisplayMode.NORMAL
    BhiFontDisplayMode.SEE_THROUGH -> Font.DisplayMode.SEE_THROUGH
    BhiFontDisplayMode.POLYGON_OFFSET -> Font.DisplayMode.POLYGON_OFFSET
}
//?}

//? if >=26.1 {
/**
 * 26.1+ 的加载器无关世界渲染上下文。
 *
 * Fabric 与 NeoForge 都使用原版的提交节点渲染管线，但提供上下文的事件类型不同；
 * 在边界处转换成这个小对象后，实际血条渲染代码无需感知加载器。
 */
class BhiWorldRenderContext(
    private val stack: PoseStack,
    private val collector: net.minecraft.client.renderer.SubmitNodeCollector,
    private val state: net.minecraft.client.renderer.state.level.LevelRenderState,
) {
    fun poseStack(): PoseStack = stack
    fun submitNodeCollector(): net.minecraft.client.renderer.SubmitNodeCollector = collector
    fun levelState(): net.minecraft.client.renderer.state.level.LevelRenderState = state
}
//?} else if >=1.21.9 {
/*typealias BhiWorldRenderContext = net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext*/
//?} else if >=1.16 {
/*typealias BhiWorldRenderContext = net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext*/
//?} else {
/*class BhiWorldRenderContext(
    private val stack: PoseStack,
    private val bufferSource: net.minecraft.client.renderer.MultiBufferSource,
    private val renderCamera: net.minecraft.client.Camera,
) {
    fun matrixStack(): PoseStack = stack
    fun consumers(): net.minecraft.client.renderer.MultiBufferSource = bufferSource
    fun camera(): net.minecraft.client.Camera = renderCamera
    fun frustum(): net.minecraft.client.renderer.culling.Frustum? = null
}*/
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
    // Submit-node billboards can face either winding depending on the camera transform.
    // Disable face culling so the health hearts remain visible on both sides.
    return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutNoCull(texture)
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
    text: BhiFormattedText,
    shadow: Boolean,
    displayMode: BhiFontDisplayMode,
    light: Int,
    color: Int,
    backgroundColor: Int,
    outlineColor: Int,
) {
    //? if >=1.21.9 {
    this.submitText(poseStack, x, y, text, shadow, displayMode.vanilla(), light, color, backgroundColor, outlineColor)
    //?} else if >=1.19 {
    /*font.drawInBatch(text, x, y, color, shadow, poseStack.last().pose(), this, displayMode.vanilla(), backgroundColor, light)*/
    //?} else if >=1.16 {
    /*font.drawInBatch(
        text, x, y, color, shadow, poseStack.last().pose(), this,
        displayMode == BhiFontDisplayMode.SEE_THROUGH, backgroundColor, light,
    )*/
    //?} else {
    /*font.drawInBatch(
        text.string, x, y, color, shadow, poseStack.last().pose(), this,
        displayMode == BhiFontDisplayMode.SEE_THROUGH, backgroundColor, light,
    )*/
    //?}
}

fun bhiVisualText(text: Component): BhiFormattedText {
    //? if >=1.16 {
    return text.visualOrderText
    //?} else {
    /*return text*/
    //?}
}
