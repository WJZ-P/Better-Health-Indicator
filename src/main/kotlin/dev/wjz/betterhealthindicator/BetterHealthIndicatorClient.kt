package dev.wjz.betterhealthindicator

import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

class BetterHealthIndicatorClient : ClientModInitializer {
    override fun onInitializeClient() {
        LOGGER.info("Better Health Indicator client initialized.")
    }

    private companion object {
        private val LOGGER = LoggerFactory.getLogger("better_health_indicator")
    }
}