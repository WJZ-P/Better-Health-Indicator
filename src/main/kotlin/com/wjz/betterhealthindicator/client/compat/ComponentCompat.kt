package com.wjz.betterhealthindicator.client.compat

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
//? if >=1.16 {
import net.minecraft.network.chat.MutableComponent
typealias BhiMutableComponent = MutableComponent
//?} else {
/*import net.minecraft.network.chat.BaseComponent
typealias BhiMutableComponent = BaseComponent*/
//?}

//? if >=1.16 {
typealias BhiConfigText = Component
//?} else {
/*typealias BhiConfigText = String*/
//?}

fun bhiLiteral(text: String): BhiMutableComponent {
    //? if >=1.19 {
    return net.minecraft.network.chat.Component.literal(text)
    //?} else {
    /*return net.minecraft.network.chat.TextComponent(text)*/
    //?}
}

fun bhiTranslatable(key: String, vararg args: Any): BhiMutableComponent {
    //? if >=1.19 {
    return net.minecraft.network.chat.Component.translatable(key, *args)
    //?} else {
    /*return net.minecraft.network.chat.TranslatableComponent(key, *args)*/
    //?}
}

fun bhiEmpty(): BhiMutableComponent = bhiLiteral("")

/** 兼容 1.20.2 前后可变文本的整型颜色快捷方法。 */
fun BhiMutableComponent.bhiColor(color: Int): BhiMutableComponent {
    //? if >=1.20.2 {
    return this.withColor(color)
    //?} else if >=1.17 {
    /*return this.setStyle(this.style.withColor(color))*/
    //?} else if >=1.16 {
    /*return this.setStyle(this.style.withColor(net.minecraft.network.chat.TextColor.fromRgb(color)))*/
    //?} else {
    /*this.setStyle(this.style.copy().setColor(bhiLegacyColor(color)))
    return this*/
    //?}
}

/** 旧版 append 返回只读 Component；统一保持可变文本类型，方便继续链式拼接。 */
fun BhiMutableComponent.bhiAppend(other: Component): BhiMutableComponent {
    this.append(other)
    return this
}

fun BhiMutableComponent.bhiBold(enabled: Boolean = true): BhiMutableComponent {
    if (!enabled) return this
    //? if >=1.16 {
    return this.withStyle(ChatFormatting.BOLD)
    //?} else {
    /*this.setStyle(this.style.copy().setBold(true))
    return this*/
    //?}
}

fun Component.bhiBoldCopy(enabled: Boolean): Component =
    if (enabled) this.copy().withStyle(ChatFormatting.BOLD) else this

fun Font.bhiWidth(text: Component): Int {
    //? if >=1.16 {
    return this.width(text)
    //?} else {
    /*return this.width(text.string)*/
    //?}
}

fun bhiConfigLiteral(text: String): BhiConfigText {
    //? if >=1.16 {
    return bhiLiteral(text)
    //?} else {
    /*return text*/
    //?}
}

fun bhiConfigTranslatable(key: String, vararg args: Any): BhiConfigText {
    //? if >=1.16 {
    return bhiTranslatable(key, *args)
    //?} else {
    /*return net.minecraft.client.resources.language.I18n.get(key, *args)*/
    //?}
}

//? if <1.16 {
/*private fun bhiLegacyColor(color: Int): ChatFormatting = when (color and 0xFFFFFF) {
    0x000000 -> ChatFormatting.BLACK
    0x0000AA, 0x5555FF -> ChatFormatting.BLUE
    0x00AA00, 0x55FF55 -> ChatFormatting.GREEN
    0x00AAAA, 0x55FFFF, 0x55AAFF -> ChatFormatting.AQUA
    0xAA0000, 0xFF5555 -> ChatFormatting.RED
    0xAA00AA, 0xFF55FF, 0xAA55FF -> ChatFormatting.LIGHT_PURPLE
    0xFFAA00 -> ChatFormatting.GOLD
    0xAAAAAA -> ChatFormatting.GRAY
    0x555555 -> ChatFormatting.DARK_GRAY
    0xFFFF55 -> ChatFormatting.YELLOW
    else -> ChatFormatting.WHITE
}*/
//?}
