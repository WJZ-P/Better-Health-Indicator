package com.wjz.betterhealthindicator.client.compat

import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity

//? if >=26.1 {
typealias BhiGuiGraphics = net.minecraft.client.gui.GuiGraphicsExtractor
//?} else if >=1.20 {
/*typealias BhiGuiGraphics = net.minecraft.client.gui.GuiGraphics*/
//?} else {
/*class BhiGuiGraphics(val poseStack: com.mojang.blaze3d.vertex.PoseStack) {
    fun guiWidth(): Int = net.minecraft.client.Minecraft.getInstance().window.guiScaledWidth

    fun pose(): com.mojang.blaze3d.vertex.PoseStack = poseStack

    fun fill(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        //? if >=1.16 {
        net.minecraft.client.gui.GuiComponent.fill(poseStack, x0, y0, x1, y1, color)
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.fill(x0, y0, x1, y1, color)*/
        //?}
    }

    fun fillGradient(x0: Int, y0: Int, x1: Int, y1: Int, topColor: Int, bottomColor: Int) {
        val height = (y1 - y0).coerceAtLeast(1)
        for (y in y0 until y1) {
            val t = (y - y0).toFloat() / height.toFloat()
            val a = (((topColor ushr 24) and 0xFF) * (1.0f - t) + ((bottomColor ushr 24) and 0xFF) * t).toInt()
            val r = (((topColor ushr 16) and 0xFF) * (1.0f - t) + ((bottomColor ushr 16) and 0xFF) * t).toInt()
            val g = (((topColor ushr 8) and 0xFF) * (1.0f - t) + ((bottomColor ushr 8) and 0xFF) * t).toInt()
            val b = ((topColor and 0xFF) * (1.0f - t) + (bottomColor and 0xFF) * t).toInt()
            fill(x0, y, x1, y + 1, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
    }

    fun blit(texture: BhiIdentifier, x: Int, y: Int, u: Float, v: Float, width: Int, height: Int, textureWidth: Int, textureHeight: Int) {
        //? if >=1.17 {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture)
        //?} else {
        /*net.minecraft.client.Minecraft.getInstance().textureManager.bind(texture)*/
        //?}
        //? if >=1.16 {
        net.minecraft.client.gui.GuiComponent.blit(poseStack, x, y, u, v, width, height, textureWidth, textureHeight)
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.blit(x, y, u, v, width, height, textureWidth, textureHeight)*/
        //?}
    }

    fun blitScaled(
        texture: BhiIdentifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        textureWidth: Int,
        textureHeight: Int,
    ) {
        //? if >=1.17 {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture)
        //?} else {
        /*net.minecraft.client.Minecraft.getInstance().textureManager.bind(texture)*/
        //?}
        //? if >=1.16 {
        net.minecraft.client.gui.GuiComponent.blit(
            poseStack,
            x, y, width, height,
            u, v,
            sourceWidth, sourceHeight,
            textureWidth, textureHeight,
        )
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.blit(
            x, y, width, height,
            u, v,
            sourceWidth, sourceHeight,
            textureWidth, textureHeight,
        )*/
        //?}
    }

    fun blit(x: Int, y: Int, z: Int, width: Int, height: Int, sprite: net.minecraft.client.renderer.texture.TextureAtlasSprite) {
        //? if >=1.16 {
        net.minecraft.client.gui.GuiComponent.blit(poseStack, x, y, z, width, height, sprite)
        //?} else {
        /*net.minecraft.client.gui.GuiComponent.blit(x, y, z, width, height, sprite)*/
        //?}
    }
}*/
//?}

//? if >=1.21.2 {
typealias BhiEntityRenderState = net.minecraft.client.renderer.entity.state.EntityRenderState
//?} else {
/*typealias BhiEntityRenderState = LivingEntity*/
//?}

fun BhiGuiGraphics.bhiText(font: Font, text: Component, x: Int, y: Int, color: Int, shadow: Boolean) {
    //? if >=26.1 {
    this.text(font, text, x, y, color, shadow)
    //?} else if >=1.20 {
    /*this.drawString(font, text, x, y, color, shadow)*/
    //?} else if >=1.16 {
    /*net.minecraft.client.gui.GuiComponent.drawString(this.poseStack, font, text, x, y, color)*/
    //?} else {
    /*if (shadow) font.drawShadow(text.string, x.toFloat(), y.toFloat(), color)
    else font.draw(text.string, x.toFloat(), y.toFloat(), color)*/
    //?}
}

fun BhiGuiGraphics.bhiOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {
    //? if >=26.1 {
    this.outline(x, y, width, height, color)
    //?} else if >=1.20 {
    /*this.renderOutline(x, y, width, height, color)*/
    //?} else {
    /*this.fill(x, y, x + width, y + 1, color)
    this.fill(x, y + height - 1, x + width, y + height, color)
    this.fill(x, y + 1, x + 1, y + height - 1, color)
    this.fill(x + width - 1, y + 1, x + width, y + height - 1, color)*/
    //?}
}

fun BhiGuiGraphics.bhiBlitSprite(sprite: BhiIdentifier, x: Int, y: Int, width: Int, height: Int) {
    //? if >=1.21.5 {
    this.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height)
    //?} else if >=1.21.2 {
    /*this.blitSprite({ texture -> net.minecraft.client.renderer.RenderType.guiTextured(texture) }, sprite, x, y, width, height)*/
    //?} else if >=1.20.2 {
    /*this.blitSprite(sprite, x, y, width, height)*/
    //?} else {
    /*when (sprite.path) {
        "hud/heart/container" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 16.0f, 0.0f, width, height, 256, 256)
        "hud/heart/container_blinking" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 25.0f, 0.0f, width, height, 256, 256)
        "hud/heart/container_hardcore" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 16.0f, 45.0f, width, height, 256, 256)
        "hud/heart/container_hardcore_blinking" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 25.0f, 45.0f, width, height, 256, 256)
        "hud/heart/full" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 52.0f, 0.0f, width, height, 256, 256)
        "hud/heart/half" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 61.0f, 0.0f, width, height, 256, 256)
        "hud/heart/hardcore_full" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 52.0f, 45.0f, width, height, 256, 256)
        "hud/heart/hardcore_half" -> this.blit(bhiVanillaIdentifier("textures/gui/icons.png"), x, y, 61.0f, 45.0f, width, height, 256, 256)
        "hud/effect_background" -> this.fill(x, y, x + width, y + height, 0xA0202020.toInt())
        "hud/effect_background_ambient" -> this.fill(x, y, x + width, y + height, 0xA0404040.toInt())
        "frame/square", "frame/round" -> this.blitScaled(
            bhiIdentifier(sprite.namespace, "textures/gui/sprites/${sprite.path}.png"),
            x, y, width, height,
            0.0f, 0.0f,
            26, 26,
            26, 26,
        )
        else -> this.blit(sprite, x, y, 0.0f, 0.0f, width, height, width, height)
    }*/
    //?}
}

fun BhiGuiGraphics.bhiBlitMobEffectSprite(sprite: BhiMobEffectSprite, x: Int, y: Int, width: Int, height: Int) {
    //? if >=1.21.5 {
    this.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height)
    //?} else if >=1.21.2 {
    /*this.blitSprite({ texture -> net.minecraft.client.renderer.RenderType.guiTextured(texture) }, sprite, x, y, width, height)*/
    //?} else {
    /*this.blit(x, y, 0, width, height, sprite)*/
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
    //?} else if >=1.21.2 {
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
    //?} else {
    /*this.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)*/
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
    //?} else if >=1.19 {
    /*this.pose().translate(x, y, 0.0f)*/
    //?} else {
    /*this.pose().translate(x.toDouble(), y.toDouble(), 0.0)*/
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
    state: BhiEntityRenderState,
    scale: Float,
    translation: BhiVector3f,
    rotation: BhiQuaternionf,
    cameraRotation: BhiQuaternionf,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
) {
    //? if >=26.1 {
    this.entity(state, scale, translation, rotation, cameraRotation, x0, y0, x1, y1)
    //?} else if >=1.21.5 {
    /*this.submitEntityRenderState(state, scale, translation, rotation, cameraRotation, x0, y0, x1, y1)*/
    //?} else if >=1.21 {
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
    //?} else if >=1.20.2 {
    /*net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
        this,
        (x0 + x1) / 2.0f,
        (y0 + y1) / 2.0f,
        scale.toInt(),
        translation,
        rotation,
        cameraRotation,
        entity,
    )*/
    //?} else if >=1.20 {
    /*net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
        this,
        (x0 + x1) / 2,
        ((y0 + y1) / 2.0f + translation.bhiY() * scale).toInt(),
        scale.toInt(),
        rotation,
        cameraRotation,
        entity,
    )*/
    //?} else if >=1.19 {
    /*net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
        this.poseStack,
        (x0 + x1) / 2,
        ((y0 + y1) / 2.0f + translation.bhiY() * scale).toInt(),
        scale.toInt(),
        rotation,
        cameraRotation,
        entity,
    )*/
    //?} else {
    /*net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(
        (x0 + x1) / 2,
        ((y0 + y1) / 2.0f + translation.bhiY() * scale).toInt(),
        scale.toInt(),
        0.0f,
        0.0f,
        entity,
    )*/
    //?}
}
