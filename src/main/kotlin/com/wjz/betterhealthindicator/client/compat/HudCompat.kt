package com.wjz.betterhealthindicator.client.compat

/** 注册 HUD 图层，并把不同版本的帧时间对象统一转换为 tickProgress。 */
fun registerBhiHud(
    id: BhiIdentifier,
    renderer: (BhiGuiGraphics, Float) -> Unit,
) {
    //? if >=1.21.5 {
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
        id,
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement { graphics, delta ->
            renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
        },
    )
    //?} else {
    /*net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback.EVENT.register { drawer ->
        drawer.addLayer(
            net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer.of(id) { graphics, delta ->
                renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
            },
        )
    }*/
    //?}
}
