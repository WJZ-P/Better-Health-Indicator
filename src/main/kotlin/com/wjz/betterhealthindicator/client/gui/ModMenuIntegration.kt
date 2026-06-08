package com.wjz.betterhealthindicator.client.gui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.AlertScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * ModMenu 入口：在模组列表中为本模组提供"设置"按钮。
 *
 * 仅当安装了 ModMenu 时该入口才会被加载，因此 ModMenu / Cloth Config 均为可选依赖。
 */
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        // Cloth Config 为可选(suggests)依赖。未安装时绝不能触碰 ClothConfigScreenFactory，
        // 否则其对 me.shedaniel.clothconfig2.* 的引用会在类链接阶段抛 NoClassDefFoundError 导致崩溃。
        // 这里在调用前用 isModLoaded 拦截，缺失时给出不依赖 Cloth 的原版提示界面作为回退。
        if (!FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG_MOD_ID)) {
            return ConfigScreenFactory<Screen> { parent ->
                AlertScreen(
                    { Minecraft.getInstance().setScreen(parent) },
                    Component.literal("Better Health Indicator"),
                    Component.literal("需要安装 Cloth Config 才能打开图形化设置界面，配置仍可在 config/better_health_indicator.json 手动编辑。"),
                )
            }
        }
        return ConfigScreenFactory<Screen> { parent -> ClothConfigScreenFactory.create(parent) }
    }

    private companion object {
        const val CLOTH_CONFIG_MOD_ID = "cloth-config"
    }
}
