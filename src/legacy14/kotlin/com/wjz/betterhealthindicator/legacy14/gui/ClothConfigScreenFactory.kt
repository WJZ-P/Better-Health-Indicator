package com.wjz.betterhealthindicator.legacy14.gui

import net.minecraft.client.gui.screens.Screen

/** Cloth Config 1.x 的 API 与现代配置页不兼容；保留稳定的 ModMenu 回退入口。 */
object ClothConfigScreenFactory {
    fun create(parent: Screen): Screen = parent
}
