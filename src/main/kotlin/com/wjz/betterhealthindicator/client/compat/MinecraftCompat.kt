package com.wjz.betterhealthindicator.client.compat

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffect

/** 集中收敛 Minecraft 小版本间的名称和访问器变化。 */
object MinecraftCompat {
    fun setScreen(minecraft: Minecraft, screen: Screen) {
        minecraft.setScreenAndShow(screen)
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

    fun mobEffectSprite(effect: Holder<MobEffect>): Identifier {
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
}
