package com.wjz.betterhealthindicator.client.gui

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.minecraft.client.gui.screens.Screen

/**
 * ModMenu 入口：在模组列表中为本模组提供"设置"按钮。
 *
 * 仅当安装了 ModMenu 时该入口才会被加载，因此 ModMenu / Cloth Config 均为可选依赖。
 */
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory<Screen> { parent -> ClothConfigScreenFactory.create(parent) }
}
