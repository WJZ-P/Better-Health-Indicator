package com.wjz.betterhealthindicator.client.compat

// Mojang 在 1.21.9 将 ResourceLocation 重命名为 Identifier。
//? if >=1.21.9 {
typealias BhiIdentifier = net.minecraft.resources.Identifier
//?} else {
/*typealias BhiIdentifier = net.minecraft.resources.ResourceLocation*/
//?}
