package com.wjz.betterhealthindicator;

import com.wjz.betterhealthindicator.legacy.LegacyForgeHeartRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(
        modid = BetterHealthIndicatorNetease.MOD_ID,
        name = BetterHealthIndicatorNetease.MOD_NAME,
        version = BetterHealthIndicatorNetease.VERSION,
        acceptedMinecraftVersions = "[1.11.2]",
        clientSideOnly = true
)
public final class BetterHealthIndicatorNetease {
    public static final String MOD_ID = "better_health_indicator";
    public static final String MOD_NAME = "Better Health Indicator";
    public static final String VERSION = "1.0.0+netease1.11.2";

    @Mod.EventHandler
    public void onInitialize(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new LegacyForgeHeartRenderer());
    }
}
