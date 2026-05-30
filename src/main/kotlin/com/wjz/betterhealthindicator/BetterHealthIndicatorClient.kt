package com.wjz.betterhealthindicator

import com.wjz.betterhealthindicator.client.hud.HealthPanelHud
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer
import com.wjz.betterhealthindicator.config.ConfigManager
import net.fabricmc.api.ClientModInitializer

class BetterHealthIndicatorClient : ClientModInitializer {
    override fun onInitializeClient() {
        ConfigManager.load()
        EntityHealthBarRenderer.register()
        HealthPanelHud.register()
        BetterHealthIndicatorLogger.info("Better Health Indicator client initialized.")
    }
}
