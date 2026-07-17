package com.wjz.betterhealthindicator.legacyfabric.mixin;

import com.wjz.betterhealthindicator.legacyfabric.LegacyHealthRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;DDDFF)V", at = @At("TAIL"))
    private void betterHealthIndicator$renderLegacyBar(
            LivingEntity entity,
            double x,
            double y,
            double z,
            float yaw,
            float tickDelta,
            CallbackInfo ci
    ) {
        LegacyHealthRenderer.renderWorldBar(entity, x, y, z, tickDelta);
    }
}
