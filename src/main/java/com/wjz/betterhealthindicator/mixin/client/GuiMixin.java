package com.wjz.betterhealthindicator.mixin.client;

//? if >=1.15 {
import com.wjz.betterhealthindicator.client.hud.HealthPanelHud;
//?}
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.14 及更早 HUD 回调的兼容入口；新版本为空 Mixin。 */
@Mixin(Gui.class)
public class GuiMixin {
    //? if <1.15 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void betterHealthIndicator$renderLegacyHud(float tickProgress, CallbackInfo ci) {
        com.wjz.betterhealthindicator.legacy14.hud.HealthPanelHud.INSTANCE.renderLegacy(tickProgress);
    }*/
    //?}
}
