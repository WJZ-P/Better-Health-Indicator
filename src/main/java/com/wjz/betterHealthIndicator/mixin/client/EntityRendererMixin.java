package com.wjz.betterHealthIndicator.mixin.client;

import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer;
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
 * 仅当 {@link EntityHealthBarRenderer#shouldHideVanillaName(int)} 命中时返回 {@code false}（只隐藏、
 * 绝不强显），因此对未被本模组绘制血条的实体（超距 / 未注视 / 被过滤）保持原版行为，名字不会凭空消失。</p>
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void betterHealthIndicator$suppressVanillaNameTag(
            Entity entity,
            double distanceToCameraSq,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (EntityHealthBarRenderer.INSTANCE.shouldHideVanillaName(entity.getId())) {
            cir.setReturnValue(false);
        }
    }
}
