package dev.wjz.betterhealthindicator

import dev.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer
import net.fabricmc.api.ClientModInitializer

class BetterHealthIndicatorClient : ClientModInitializer {
    override fun onInitializeClient() {
        EntityHealthBarRenderer.register()
        BetterHealthIndicatorLogger.info("Better Health Indicator client initialized.")
    }
}