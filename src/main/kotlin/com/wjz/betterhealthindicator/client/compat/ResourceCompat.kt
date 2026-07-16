package com.wjz.betterhealthindicator.client.compat

// Mojang 在 1.21.9 将 ResourceLocation 重命名为 Identifier。
//? if >=1.21.9 {
typealias BhiIdentifier = net.minecraft.resources.Identifier
//?} else {
/*typealias BhiIdentifier = net.minecraft.resources.ResourceLocation*/
//?}

// 1.21.5 起 HUD 状态效果 helper 返回 GUI sprite id；旧版直接返回图集中的 sprite。
//? if >=1.21.5 {
typealias BhiMobEffectSprite = BhiIdentifier
//?} else {
/*typealias BhiMobEffectSprite = net.minecraft.client.renderer.texture.TextureAtlasSprite*/
//?}
