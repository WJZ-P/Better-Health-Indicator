package com.wjz.betterhealthindicator.client.compat

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
//? if >=1.15 {
import net.minecraft.client.multiplayer.ClientLevel
//?} else {
/*import net.minecraft.client.multiplayer.MultiPlayerLevel*/
//?}
import net.minecraft.client.gui.screens.Screen
//? if >=1.21 {
import net.minecraft.core.Holder
//?}
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

//? if >=1.15 {
typealias BhiClientLevel = ClientLevel
//?} else {
/*typealias BhiClientLevel = MultiPlayerLevel*/
//?}

//? if >=1.21 {
typealias BhiMobEffectRef = Holder<MobEffect>
//?} else {
/*typealias BhiMobEffectRef = MobEffect*/
//?}

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

    fun mobEffectSprite(effect: BhiMobEffectRef): BhiMobEffectSprite {
        //? if >=26.2 {
        /*return net.minecraft.client.gui.Hud.getMobEffectSprite(effect)*/
        //?} else if >=1.21.5 {
        return net.minecraft.client.gui.Gui.getMobEffectSprite(effect)
        //?} else {
        /*return Minecraft.getInstance().mobEffectTextures.get(effect)*/
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
        //?} else if >=1.17 {
        /*return net.minecraft.client.renderer.LightTexture.FULL_BRIGHT*/
        //?} else {
        /*return 0x00F000F0*/
        //?}
    }

    fun displayName(entity: Entity): Component {
        //? if >=1.21.9 {
        return entity.displayName
        //?} else {
        /*return entity.displayName ?: entity.name*/
        //?}
    }

    fun cameraForward(camera: Camera): Vec3 {
        //? if >=1.21.9 {
        val forward = camera.forwardVector()
        return Vec3(forward.x().toDouble(), forward.y().toDouble(), forward.z().toDouble())
        //?} else if >=1.15 {
        /*val forward = camera.lookVector
        return Vec3(forward.x().toDouble(), forward.y().toDouble(), forward.z().toDouble())*/
        //?} else {
        /*return camera.lookVector*/
        //?}
    }

    fun cameraPosition(camera: Camera): Vec3 {
        //? if >=1.21.5 {
        return camera.position()
        //?} else {
        /*return camera.position*/
        //?}
    }

    fun tickProgress(minecraft: Minecraft): Float {
        //? if >=1.21.2 {
        return minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        //?} else {
        /*return minecraft.frameTime*/
        //?}
    }

    fun shouldRunTicks(level: BhiClientLevel): Boolean {
        //? if >=1.20.3 {
        return level.tickRateManager().runsNormally()
        //?} else {
        /*return true*/
        //?}
    }

    fun level(entity: Entity): Level {
        //? if >=1.20 {
        return entity.level()
        //?} else {
        /*return entity.level*/
        //?}
    }

    fun entityPosition(entity: Entity, tickProgress: Float): Vec3 {
        //? if >=1.16 {
        return entity.getPosition(tickProgress)
        //?} else {
        /*return Vec3(
            net.minecraft.util.Mth.lerp(tickProgress.toDouble(), entity.xOld, entity.x),
            net.minecraft.util.Mth.lerp(tickProgress.toDouble(), entity.yOld, entity.y),
            net.minecraft.util.Mth.lerp(tickProgress.toDouble(), entity.zOld, entity.z),
        )*/
        //?}
    }


    fun fov(minecraft: Minecraft): Double {
        //? if >=1.19 {
        return minecraft.options.fov().get().toDouble()
        //?} else {
        /*return minecraft.options.fov*/
        //?}
    }
}
