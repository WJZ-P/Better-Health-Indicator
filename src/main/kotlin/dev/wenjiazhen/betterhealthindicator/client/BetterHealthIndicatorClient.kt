package dev.wenjiazhen.betterhealthindicator.client

import dev.wenjiazhen.betterhealthindicator.BetterHealthIndicatorMod
import net.fabricmc.api.ClientModInitializer

object BetterHealthIndicatorClient : ClientModInitializer {
    override fun onInitializeClient() {
        HealthBarOverlay.register()
        BetterHealthIndicatorMod.logger.info("Better Health Indicator client is ready.")
    }
}
