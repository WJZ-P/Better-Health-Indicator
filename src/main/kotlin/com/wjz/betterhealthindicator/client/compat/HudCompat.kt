package com.wjz.betterhealthindicator.client.compat

//? if neoforge || >=26.1 {
import com.wjz.betterhealthindicator.platform.BhiPlatformHooks
//?}

/** 注册 HUD 图层，并把不同版本的帧时间对象统一转换为 tickProgress。 */
fun registerBhiHud(
    id: BhiIdentifier,
    renderer: (BhiGuiGraphics, Float) -> Unit,
) {
    //? if neoforge || >=26.1 {
    BhiPlatformHooks.registerHud(id, renderer)
    //?} else if >=1.21.5 {
    /*
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
        net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.BOSS_BAR,
        id,
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement { graphics, delta ->
            renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
        },
    )
    */
    //?} else if >=1.21.4 {
    /*net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback.EVENT.register { drawer ->
        drawer.addLayer(
            net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer.of(id) { graphics, delta ->
                renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
            },
        )
    }*/
    //?} else if >=1.21 {
    /*net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register { graphics, delta ->
        renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
    }*/
    //?} else if >=1.20 {
    /*net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register { graphics, tickDelta ->
        renderer(graphics, tickDelta)
    }*/
    //?} else if >=1.16 {
    /*net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register { poseStack, tickDelta ->
        renderer(BhiGuiGraphics(poseStack), tickDelta)
    }*/
    //?} else {
    /*net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register { tickDelta ->
        renderer(BhiGuiGraphics(com.mojang.blaze3d.vertex.PoseStack()), tickDelta)
    }*/
    //?}
}
