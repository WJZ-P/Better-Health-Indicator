package com.wjz.betterhealthindicator.mixin.client;

//? if >=1.15 {
import com.wjz.betterhealthindicator.client.hud.HealthPanelHud;
//?}
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if forge && >=1.20.5 && <1.21.1 {
/*import com.wjz.betterhealthindicator.platform.BhiPlatformHooks;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
*/
//?}

/** 1.14 及更早 HUD 回调的兼容入口；新版本为空 Mixin。 */
//? if forge && >=1.20.5 {
/*@Mixin(value = Gui.class, remap = false)*/
//?} else {
@Mixin(Gui.class)
//?}
public class GuiMixin {
    //? if forge && >=1.21 && <1.21.1 {
    @Inject(method = "render", at = @At("TAIL"))
    private void betterHealthIndicator$renderForge51Hud(
            GuiGraphics graphics,
            DeltaTracker delta,
            CallbackInfo ci
    ) {
        BhiPlatformHooks.dispatchHud(graphics, delta.getGameTimeDeltaPartialTick(false));
    }
    //?} else if forge && >=1.20.5 && <1.21 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void betterHealthIndicator$renderForgeLayeredHud(
            GuiGraphics graphics,
            float tickProgress,
            CallbackInfo ci
    ) {
        BhiPlatformHooks.dispatchHud(graphics, tickProgress);
    }
    */
    //?}
    //? if <1.15 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void betterHealthIndicator$renderLegacyHud(float tickProgress, CallbackInfo ci) {
        com.wjz.betterhealthindicator.legacy14.hud.HealthPanelHud.INSTANCE.renderLegacy(tickProgress);
    }*/
    //?}
}
