package com.wjz.betterhealthindicator.legacyfabric.mixin;

import com.wjz.betterhealthindicator.legacyfabric.LegacyHealthRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render(F)V", at = @At("TAIL"))
    private void betterHealthIndicator$renderLegacyHud(float tickDelta, CallbackInfo ci) {
        LegacyHealthRenderer.renderHud();
    }
}
