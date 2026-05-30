package com.wjz.betterhealthindicator.config

import com.google.gson.GsonBuilder
import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import net.fabricmc.loader.api.FabricLoader
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
        FabricLoader.getInstance().configDir.resolve("better_health_indicator.json")

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
