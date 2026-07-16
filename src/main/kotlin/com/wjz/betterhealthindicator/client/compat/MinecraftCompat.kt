package com.wjz.betterhealthindicator.client.compat

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.Entity
import org.joml.Vector3fc

/** 集中收敛 Minecraft 小版本间的名称和访问器变化。 */
object MinecraftCompat {
    fun setScreen(minecraft: Minecraft, screen: Screen) {
        //? if >=1.21.9 {
        minecraft.setScreenAndShow(screen)
        //?} else {
        /*minecraft.setScreen(screen)*/
        //?}
    }

    fun isHudHidden(minecraft: Minecraft): Boolean {
        //? if >=26.2 {
        /*return minecraft.gui.hud.isHidden*/
        //?} else {
        return minecraft.options.hideGui
        //?}
    }

    fun mainCamera(minecraft: Minecraft): Camera {
        //? if >=26.2 {
        /*return minecraft.gameRenderer.mainCamera()*/
        //?} else {
        return minecraft.gameRenderer.mainCamera
        //?}
    }

    fun mobEffectSprite(effect: Holder<MobEffect>): BhiIdentifier {
        //? if >=26.2 {
        /*return net.minecraft.client.gui.Hud.getMobEffectSprite(effect)*/
        //?} else {
        return net.minecraft.client.gui.Gui.getMobEffectSprite(effect)
        //?}
    }

    fun isInstantaneous(effect: MobEffect): Boolean {
        //? if >=26.2 {
        /*return effect.isInstantaneous*/
        //?} else {
        return effect.isInstantenous
        //?}
    }

    fun fullBright(): Int {
        //? if >=26.1 {
        return net.minecraft.util.LightCoordsUtil.FULL_BRIGHT
        //?} else {
        /*return net.minecraft.client.renderer.LightTexture.FULL_BRIGHT*/
        //?}
    }

    fun displayName(entity: Entity): Component {
        //? if >=1.21.9 {
        return entity.displayName
        //?} else {
        /*return entity.displayName ?: entity.name*/
        //?}
    }

    fun cameraForward(camera: Camera): Vector3fc {
        //? if >=1.21.9 {
        return camera.forwardVector()
        //?} else {
        /*return camera.lookVector*/
        //?}
    }
}
