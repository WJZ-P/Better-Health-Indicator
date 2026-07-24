package com.wjz.betterhealthindicator;

import com.wjz.betterhealthindicator.client.hud.HealthPanelHud;
import com.wjz.betterhealthindicator.client.render.AttackTracker;
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer;
import com.wjz.betterhealthindicator.client.render.MobEffectParticleIndex;
import com.wjz.betterhealthindicator.client.render.TintedHeartTextures;
import com.wjz.betterhealthindicator.config.ConfigManager;
import net.minecraftforge.fml.common.Mod;

/**
 * NetEase Java Edition client bootstrap.
 *
 * <p>The distribution JAR is self-contained: Kotlin's standard library is
 * shaded and relocated during packaging, so the China Edition runtime does
 * not need KotlinForForge or any other prerequisite mod.</p>
 */
@Mod(BetterHealthIndicatorNetease.MOD_ID)
public final class BetterHealthIndicatorNetease {
    public static final String MOD_ID = "better_health_indicator";

    public BetterHealthIndicatorNetease() {
        ConfigManager.INSTANCE.load();
        AttackTracker.INSTANCE.register();
        EntityHealthBarRenderer.INSTANCE.register();
        HealthPanelHud.INSTANCE.register();
        MobEffectParticleIndex.INSTANCE.init();
        TintedHeartTextures.INSTANCE.init();

        BetterHealthIndicatorLogger.INSTANCE.info("Better Health Indicator NetEase client initialized.");
    }
}
