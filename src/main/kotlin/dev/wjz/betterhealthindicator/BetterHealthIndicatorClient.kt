package dev.wjz.betterhealthindicator

import dev.wjz.betterhealthindicator.client.hud.HealthPanelHud
import dev.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer
import dev.wjz.betterhealthindicator.config.ConfigManager
import net.fabricmc.api.ClientModInitializer

class BetterHealthIndicatorClient : ClientModInitializer {
    override fun onInitializeClient() {
        ConfigManager.load()
        EntityHealthBarRenderer.register()
        HealthPanelHud.register()
        BetterHealthIndicatorLogger.info("Better Health Indicator client initialized.")
    }
}
