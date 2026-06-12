package com.wjz.betterhealthindicator

import com.wjz.betterhealthindicator.client.hud.HealthPanelHud
import com.wjz.betterhealthindicator.client.render.AttackTracker
import com.wjz.betterhealthindicator.client.render.EntityHealthBarRenderer
import com.wjz.betterhealthindicator.client.render.MobEffectParticleIndex
import com.wjz.betterhealthindicator.client.render.TintedHeartTextures
import com.wjz.betterhealthindicator.config.ConfigManager
import net.fabricmc.api.ClientModInitializer

class BetterHealthIndicatorClient : ClientModInitializer {
    override fun onInitializeClient() {
        ConfigManager.load()
        AttackTracker.register()
        EntityHealthBarRenderer.register()
        HealthPanelHud.register()
        // 预热「效果粒子 → 状态效果」动态映射表（注册表此时已就绪），供面板药水图标兜底反查。
        MobEffectParticleIndex.init()
        // 预载多重血条上层染色心的灰度模板（资源未就绪时不会锁定，渲染时会自动重试）。
        TintedHeartTextures.init()
        BetterHealthIndicatorLogger.info("Better Health Indicator client initialized.")
    }
}
