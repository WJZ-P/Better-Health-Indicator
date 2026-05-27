package dev.wenjiazhen.betterhealthindicator

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object BetterHealthIndicatorMod : ModInitializer {
    const val MOD_ID = "better_health_indicator"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        logger.info("Better Health Indicator is ready.")
    }
}
