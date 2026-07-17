package com.wjz.betterhealthindicator.mixin.client;

//? if >=1.15 {
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer;
//?}
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.15 世界渲染回调的兼容入口；1.16+ 是无注入的空 Mixin。 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    //? if >=1.15 && <1.16 {
    /*@Inject(method = "renderLevel", at = @At("TAIL"))
    private void betterHealthIndicator$renderLegacy(
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            float tickProgress,
            long finishTimeNano,
            boolean renderBlockOutline,
            net.minecraft.client.Camera camera,
            net.minecraft.client.renderer.GameRenderer gameRenderer,
            net.minecraft.client.renderer.LightTexture lightTexture,
            com.mojang.math.Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        EntityHealthBarRenderer.INSTANCE.renderLegacy(poseStack, tickProgress, camera);
    }*/
    //?} else if <1.15 {
    /*@Inject(method = "renderEntities", at = @At("TAIL"))
    private void betterHealthIndicator$renderLegacyOpenGl(
            net.minecraft.client.Camera camera,
            net.minecraft.client.renderer.culling.Culler culler,
            float tickProgress,
            CallbackInfo ci
    ) {
        com.wjz.betterhealthindicator.legacy14.render.EntityHealthBarRenderer.INSTANCE
                .renderLegacy(camera, culler, tickProgress);
    }*/
    //?}
}
