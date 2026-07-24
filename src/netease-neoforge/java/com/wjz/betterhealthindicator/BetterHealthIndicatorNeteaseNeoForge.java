package com.wjz.betterhealthindicator;

import com.wjz.betterhealthindicator.client.hud.HealthPanelHud;
import com.wjz.betterhealthindicator.client.render.AttackTracker;
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer;
import com.wjz.betterhealthindicator.client.render.MobEffectParticleIndex;
import com.wjz.betterhealthindicator.client.render.TintedHeartTextures;
import com.wjz.betterhealthindicator.config.ConfigManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * NetEase 1.21.8 client bootstrap for the NeoForge-based MC Studio runtime.
 *
 * <p>The entrypoint deliberately uses JavaFML. Kotlin's standard library is
 * shaded and relocated into the distribution JAR, so neither KotlinForForge
 * nor another prerequisite component is required in the managed client.</p>
 */
@Mod(value = BetterHealthIndicatorNeteaseNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class BetterHealthIndicatorNeteaseNeoForge {
    public static final String MOD_ID = "better_health_indicator";

    public BetterHealthIndicatorNeteaseNeoForge() {
        ConfigManager.INSTANCE.load();
        AttackTracker.INSTANCE.register();
        EntityHealthBarRenderer.INSTANCE.register();
        HealthPanelHud.INSTANCE.register();
        MobEffectParticleIndex.INSTANCE.init();
        TintedHeartTextures.INSTANCE.init();

        BetterHealthIndicatorLogger.INSTANCE.info(
                "Better Health Indicator NetEase NeoForge client initialized."
        );
    }
}
