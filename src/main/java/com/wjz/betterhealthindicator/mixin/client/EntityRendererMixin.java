package com.wjz.betterhealthindicator.mixin.client;

//? if >=1.15 {
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer;
//?}
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 当本模组正为某实体绘制头顶血条（血条上已自带名字）时，屏蔽其原版浮空名字标签，
 * 避免「两个名字重复」并遮挡血条。
 *
 * <p>在所有实体渲染器的基类 {@link EntityRenderer#shouldShowName(Entity, double)} 处一处覆盖：
 * 仅当本模组本帧确实绘制了该实体的血条时返回 {@code false}（只隐藏、
 * 绝不强显），因此对未被本模组绘制血条的实体（超距 / 未注视 / 被过滤）保持原版行为，名字不会凭空消失。</p>
 */
//? if forge && >=1.20.5 {
/*@Mixin(value = EntityRenderer.class, remap = false)*/
//?} else {
@Mixin(EntityRenderer.class)
//?}
public class EntityRendererMixin {
    //? if >=1.21.2 {
    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void betterHealthIndicator$suppressVanillaNameTagNew(
            Entity entity,
            double distanceToCameraSq,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (EntityHealthBarRenderer.INSTANCE.shouldHideVanillaName(entity.getId())) {
            cir.setReturnValue(false);
        }
    }

    //?} else if >=1.15 {
    /*@Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void betterHealthIndicator$suppressVanillaNameTagLegacy(
            Entity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (EntityHealthBarRenderer.INSTANCE.shouldHideVanillaName(entity.getId())) {
            cir.setReturnValue(false);
        }
    }*/
    //?} else {
    /*@Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void betterHealthIndicator$suppressVanillaNameTagLegacyOpenGl(
            Entity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (com.wjz.betterhealthindicator.legacy14.render.EntityHealthBarRenderer.INSTANCE
                .shouldHideVanillaName(entity.getId())) {
            cir.setReturnValue(false);
        }
    }*/
    //?}
}
