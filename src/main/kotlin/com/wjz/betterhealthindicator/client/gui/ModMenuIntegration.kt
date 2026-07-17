package com.wjz.betterhealthindicator.client.gui

import com.wjz.betterhealthindicator.client.compat.MinecraftCompat
//? if >=1.16 {
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
//?} else if >=1.15 {
/*import io.github.prospector.modmenu.api.ConfigScreenFactory
import io.github.prospector.modmenu.api.ModMenuApi*/
//?} else {
/*import io.github.prospector.modmenu.api.ModMenuApi*/
//?}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.AlertScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.wjz.betterhealthindicator.client.compat.bhiLiteral
import com.wjz.betterhealthindicator.client.compat.bhiTranslatable

/**
 * ModMenu 入口：在模组列表中为本模组提供"设置"按钮。
 *
 * 仅当安装了 ModMenu 时该入口才会被加载，因此 ModMenu / Cloth Config 均为可选依赖。
 */
class ModMenuIntegration : ModMenuApi {
    //? if >=1.15 {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        // Cloth Config 为可选(suggests)依赖。未安装时绝不能触碰 ClothConfigScreenFactory，
        // 否则其对 me.shedaniel.clothconfig2.* 的引用会在类链接阶段抛 NoClassDefFoundError 导致崩溃。
        // 这里在调用前用 isModLoaded 拦截，缺失时给出不依赖 Cloth 的原版提示界面作为回退。
        if (!FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG_MOD_ID)) {
            return ConfigScreenFactory<Screen> { parent ->
                AlertScreen(
                    { MinecraftCompat.setScreen(Minecraft.getInstance(), parent) },
                    bhiLiteral("Better Health Indicator"),
                    bhiTranslatable("bhi.modmenu.cloth_required"),
                )
            }
        }
        return ConfigScreenFactory<Screen> { parent -> ClothConfigScreenFactory.create(parent) }
    }
    //?} else {
    /*override fun getModId(): String = "better_health_indicator"

    override fun getConfigScreenFactory(): java.util.function.Function<Screen, out Screen> =
        java.util.function.Function { parent ->
            com.wjz.betterhealthindicator.legacy14.gui.ClothConfigScreenFactory.create(parent)
        }*/
    //?}

    private companion object {
        const val CLOTH_CONFIG_MOD_ID = "cloth-config"
    }
}
