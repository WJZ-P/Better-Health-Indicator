package com.wjz.betterhealthindicator

import com.wjz.betterhealthindicator.client.gui.ClothConfigScreenFactory
import com.wjz.betterhealthindicator.client.hud.HealthPanelHud
import com.wjz.betterhealthindicator.client.render.AttackTracker
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer
import com.wjz.betterhealthindicator.client.render.MobEffectParticleIndex
import com.wjz.betterhealthindicator.client.render.TintedHeartTextures
import com.wjz.betterhealthindicator.config.ConfigManager
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

/** NeoForge 客户端入口；功能初始化顺序与 Fabric 入口保持一致。 */
@Mod(value = BetterHealthIndicatorNeoForge.MOD_ID, dist = [Dist.CLIENT])
class BetterHealthIndicatorNeoForge(modContainer: ModContainer) {
    init {
        ConfigManager.load()
        AttackTracker.register()
        EntityHealthBarRenderer.register()
        HealthPanelHud.register()
        MobEffectParticleIndex.init()
        TintedHeartTextures.init()

        if (ModList.get().isLoaded(CLOTH_CONFIG_MOD_ID)) {
            modContainer.registerExtensionPoint(
                IConfigScreenFactory::class.java,
                IConfigScreenFactory { _, parent -> ClothConfigScreenFactory.create(parent) },
            )
        }

        BetterHealthIndicatorLogger.info("Better Health Indicator NeoForge client initialized.")
    }

    companion object {
        const val MOD_ID = "better_health_indicator"
        private const val CLOTH_CONFIG_MOD_ID = "cloth_config"
    }
}
