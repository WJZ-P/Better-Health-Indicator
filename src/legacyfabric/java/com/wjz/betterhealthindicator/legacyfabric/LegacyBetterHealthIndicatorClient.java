package com.wjz.betterhealthindicator.legacyfabric;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LegacyBetterHealthIndicatorClient implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("Better Health Indicator");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Better Health Indicator Legacy Fabric backend initialized.");
    }
}
