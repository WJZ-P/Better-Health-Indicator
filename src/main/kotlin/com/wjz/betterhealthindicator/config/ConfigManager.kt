package com.wjz.betterhealthindicator.config

import com.google.gson.GsonBuilder
import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
//? if forge_like || >=26.1 {
import com.wjz.betterhealthindicator.platform.BhiPlatformHooks
//?} else {
/*
import net.fabricmc.loader.api.FabricLoader
*/
//?}
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists

/**
 * 配置读写：使用 Minecraft 自带的 GSON 持久化到 `config/better_health_indicator.json`。
 *
 * 不依赖任何第三方配置库，因此即便未安装 Cloth Config / ModMenu，配置仍可正常加载与生效。
 */
object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path =
        //? if forge_like || >=26.1 {
        BhiPlatformHooks.configDirectory().resolve("better_health_indicator.json")
        //?} else {
        /*
        FabricLoader.getInstance().configDir.resolve("better_health_indicator.json")
        */
        //?}

    @Volatile
    var config: HealthIndicatorConfig = HealthIndicatorConfig()
        private set

    fun load() {
        config = try {
            if (configPath.exists()) {
                configPath.bufferedReader().use { reader ->
                    gson.fromJson(reader, HealthIndicatorConfig::class.java) ?: HealthIndicatorConfig()
                }
            } else {
                HealthIndicatorConfig().also { saveInternal(it) }
            }
        } catch (e: Exception) {
            BetterHealthIndicatorLogger.error("Failed to load config, falling back to defaults.", e)
            HealthIndicatorConfig()
        }
        normalize(config)
    }

    /**
     * 反序列化兜底：GSON 走 Unsafe 实例化、绕过 Kotlin 字段初始化器，旧配置文件缺失新增字段时会留为 null。
     * 这里补齐可空集合型字段，避免后续访问空指针。
     */
    private fun normalize(value: HealthIndicatorConfig) {
        @Suppress("SENSELESS_COMPARISON")
        if (value.tierColors == null || value.tierColors.isEmpty()) {
            value.tierColors = HealthIndicatorConfig.Defaults.TIER_COLORS.toMutableList()
        }
        // 旧配置可能写有已移除的枚举值（如 displayMode=ALWAYS），GSON 解析失败会置 null，这里回退默认值。
        @Suppress("SENSELESS_COMPARISON")
        if (value.displayMode == null) {
            value.displayMode = HealthIndicatorConfig.Defaults.DISPLAY_MODE
        }
    }

    fun save() {
        saveInternal(config)
    }

    private fun saveInternal(value: HealthIndicatorConfig) {
        try {
            Files.createDirectories(configPath.parent)
            configPath.bufferedWriter().use { writer ->
                gson.toJson(value, writer)
            }
        } catch (e: Exception) {
            BetterHealthIndicatorLogger.error("Failed to save config.", e)
        }
    }
}
