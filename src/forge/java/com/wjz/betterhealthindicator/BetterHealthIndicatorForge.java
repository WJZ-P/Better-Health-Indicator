package com.wjz.betterhealthindicator;

import com.wjz.betterhealthindicator.client.hud.HealthPanelHud;
import com.wjz.betterhealthindicator.client.render.AttackTracker;
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer;
import com.wjz.betterhealthindicator.client.render.MobEffectParticleIndex;
import com.wjz.betterhealthindicator.client.render.TintedHeartTextures;
import com.wjz.betterhealthindicator.config.ConfigManager;
import com.wjz.betterhealthindicator.platform.BhiPlatformHooks;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge client bootstrap. KotlinForForge remains an external runtime dependency;
 * using JavaFML here avoids coupling construction to a version-specific Kotlin language loader.
 */
@Mod(BetterHealthIndicatorForge.MOD_ID)
public final class BetterHealthIndicatorForge {
    public static final String MOD_ID = "better_health_indicator";

    public BetterHealthIndicatorForge() {
        ConfigManager.INSTANCE.load();
        AttackTracker.INSTANCE.register();
        EntityHealthBarRenderer.INSTANCE.register();
        HealthPanelHud.INSTANCE.register();
        MobEffectParticleIndex.INSTANCE.init();
        TintedHeartTextures.INSTANCE.init();

        BhiPlatformHooks.INSTANCE.registerConfigScreen();

        BetterHealthIndicatorLogger.INSTANCE.info("Better Health Indicator Forge client initialized.");
    }
}
