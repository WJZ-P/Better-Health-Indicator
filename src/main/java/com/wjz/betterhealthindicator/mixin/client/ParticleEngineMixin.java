package com.wjz.betterhealthindicator.mixin.client;

import com.wjz.betterhealthindicator.config.ConfigManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截原版攻击命中时的「伤害指示」粒子（红色破碎心形 {@link ParticleTypes#DAMAGE_INDICATOR}）。
 *
 * <p>该粒子由服务端 {@code Player.attack} 通过 {@code sendParticles} 下发，客户端最终都会经
 * {@link ParticleEngine#createParticle} 实例化。在此按全局配置
 * {@code hideVanillaDamageParticles} 取消生成（返回 {@code null}），避免与本模组自绘的掉血爱心
 * 粒子重叠冲突；默认隐藏。</p>
 */
//? if forge && >=1.20.5 {
/*@Mixin(value = ParticleEngine.class, remap = false)*/
//?} else {
@Mixin(ParticleEngine.class)
//?}
public class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void betterHealthIndicator$cancelVanillaDamageParticle(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfoReturnable<Particle> cir
    ) {
        if (particleData.getType() == ParticleTypes.DAMAGE_INDICATOR
                && ConfigManager.INSTANCE.getConfig().getHideVanillaDamageParticles()) {
            cir.setReturnValue(null);
        }
    }
}
