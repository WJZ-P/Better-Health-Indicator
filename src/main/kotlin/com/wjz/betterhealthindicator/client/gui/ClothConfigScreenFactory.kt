package com.wjz.betterhealthindicator.client.gui

import com.wjz.betterhealthindicator.client.render.TintedHeartTextures
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.DisplayMode
import com.wjz.betterhealthindicator.config.HealthIndicatorConfig.Defaults
import com.wjz.betterhealthindicator.config.PanelBarStyle
import com.wjz.betterhealthindicator.config.PanelCorner
import com.wjz.betterhealthindicator.config.PanelFrameShape
import com.wjz.betterhealthindicator.config.PanelTheme
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 用 Cloth Config 构建分类设置页（全局 / 头顶血条 / 屏幕面板），保存时写回 [ConfigManager]。
 *
 * 所有文案走翻译键（`bhi.config.*`），实际文本由 `assets/.../lang/{en_us,zh_cn}.json` 提供，
 * 故英文客户端显示英文、中文客户端显示中文。新增选项时记得同步补齐两份语言文件。
 */
object ClothConfigScreenFactory {
    private fun tr(key: String): Component = Component.translatable(key)
    private fun tr(key: String, vararg args: Any): Component = Component.translatable(key, *args)

    fun create(parent: Screen): Screen {
        val config = ConfigManager.config
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Better Health Indicator"))
            .setSavingRunnable {
                ConfigManager.save()
                // 配色可能变了：丢弃旧染色贴图，下次渲染按新色重新烘焙。
                TintedHeartTextures.reset()
            }
        val entry = builder.entryBuilder()

        val global = builder.getOrCreateCategory(tr("bhi.config.category.global"))
        global.addEntry(
            entry.startBooleanToggle(tr("bhi.config.enabled"), config.enabled)
                .setDefaultValue(Defaults.ENABLED)
                .setSaveConsumer { config.enabled = it }
                .build(),
        )
        global.addEntry(
            entry.startDoubleField(tr("bhi.config.max_distance"), config.maxDistance)
                .setMin(1.0)
                .setMax(256.0)
                .setDefaultValue(Defaults.MAX_DISTANCE)
                .setSaveConsumer { config.maxDistance = it }
                .build(),
        )
        global.addEntry(
            entry.startEnumSelector(tr("bhi.config.display_mode"), DisplayMode::class.java, config.displayMode)
                .setDefaultValue(Defaults.DISPLAY_MODE)
                .setEnumNameProvider { tr("bhi.config.enum.display_mode.${it.name.lowercase()}") }
                .setSaveConsumer { config.displayMode = it }
                .build(),
        )
        global.addEntry(
            entry.startBooleanToggle(tr("bhi.config.track_attacked"), config.trackAttacked)
                .setDefaultValue(Defaults.TRACK_ATTACKED)
                .setTooltip(tr("bhi.config.track_attacked.tip"))
                .setSaveConsumer { config.trackAttacked = it }
                .build(),
        )
        global.addEntry(
            entry.startDoubleField(tr("bhi.config.attack_tracking_seconds"), config.attackTrackingSeconds)
                .setMin(1.0)
                .setMax(60.0)
                .setDefaultValue(Defaults.ATTACK_TRACKING_SECONDS)
                .setTooltip(tr("bhi.config.attack_tracking_seconds.tip"))
                .setSaveConsumer { config.attackTrackingSeconds = it }
                .build(),
        )
        global.addEntry(
            entry.startBooleanToggle(tr("bhi.config.hide_vanilla_damage_particles"), config.hideVanillaDamageParticles)
                .setDefaultValue(Defaults.HIDE_VANILLA_DAMAGE_PARTICLES)
                .setTooltip(tr("bhi.config.hide_vanilla_damage_particles.tip"))
                .setSaveConsumer { config.hideVanillaDamageParticles = it }
                .build(),
        )

        val head = builder.getOrCreateCategory(tr("bhi.config.category.head"))
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.show_name"), config.showName)
                .setDefaultValue(Defaults.SHOW_NAME)
                .setSaveConsumer { config.showName = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.show_health_text"), config.showHealthText)
                .setDefaultValue(Defaults.SHOW_HEALTH_TEXT)
                .setTooltip(tr("bhi.config.show_health_text.tip"))
                .setSaveConsumer { config.showHealthText = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.detail_beside_bar"), config.detailBesideBar)
                .setDefaultValue(Defaults.DETAIL_BESIDE_BAR)
                .setTooltip(tr("bhi.config.detail_beside_bar.tip"))
                .setSaveConsumer { config.detailBesideBar = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.head_heart_hit_effect"), config.headHeartHitEffect)
                .setDefaultValue(Defaults.HEAD_HEART_HIT_EFFECT)
                .setTooltip(tr("bhi.config.head_heart_hit_effect.tip"))
                .setSaveConsumer { config.headHeartHitEffect = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(tr("bhi.config.low_health_shake_threshold"), config.lowHealthShakeThreshold)
                .setMin(0.0)
                .setMax(1.0)
                .setDefaultValue(Defaults.LOW_HEALTH_SHAKE_THRESHOLD)
                .setTooltip(tr("bhi.config.low_health_shake_threshold.tip"))
                .setSaveConsumer { config.lowHealthShakeThreshold = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.hide_vanilla_name_tag"), config.hideVanillaNameTag)
                .setDefaultValue(Defaults.HIDE_VANILLA_NAME_TAG)
                .setTooltip(tr("bhi.config.hide_vanilla_name_tag.tip"))
                .setSaveConsumer { config.hideVanillaNameTag = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(tr("bhi.config.text_scale"), config.textScale)
                .setMin(0.3)
                .setMax(3.0)
                .setDefaultValue(Defaults.TEXT_SCALE)
                .setTooltip(tr("bhi.config.text_scale.tip"))
                .setSaveConsumer { config.textScale = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(tr("bhi.config.health_text_scale"), config.healthTextScale)
                .setMin(0.3)
                .setMax(3.0)
                .setDefaultValue(Defaults.HEALTH_TEXT_SCALE)
                .setTooltip(tr("bhi.config.health_text_scale.tip"))
                .setSaveConsumer { config.healthTextScale = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.text_bold"), config.textBold)
                .setDefaultValue(Defaults.TEXT_BOLD)
                .setTooltip(tr("bhi.config.text_bold.tip"))
                .setSaveConsumer { config.textBold = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.occlude_behind_walls"), config.occludeBehindWalls)
                .setDefaultValue(Defaults.OCCLUDE_BEHIND_WALLS)
                .setSaveConsumer { config.occludeBehindWalls = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.show_full_health_entities"), config.showFullHealthEntities)
                .setDefaultValue(Defaults.SHOW_FULL_HEALTH_ENTITIES)
                .setSaveConsumer { config.showFullHealthEntities = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.show_self"), config.showSelf)
                .setDefaultValue(Defaults.SHOW_SELF)
                .setSaveConsumer { config.showSelf = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(tr("bhi.config.y_offset"), config.yOffset)
                .setMin(-2.0)
                .setMax(3.0)
                .setDefaultValue(Defaults.Y_OFFSET)
                .setTooltip(tr("bhi.config.y_offset.tip"))
                .setSaveConsumer { config.yOffset = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.damage_particles_enabled"), config.damageParticlesEnabled)
                .setDefaultValue(Defaults.DAMAGE_PARTICLES_ENABLED)
                .setTooltip(tr("bhi.config.damage_particles_enabled.tip"))
                .setSaveConsumer { config.damageParticlesEnabled = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(tr("bhi.config.particle_shake_scale"), config.particleShakeScale)
                .setMin(0.0)
                .setMax(3.0)
                .setDefaultValue(Defaults.PARTICLE_SHAKE_SCALE)
                .setTooltip(tr("bhi.config.particle_shake_scale.tip"))
                .setSaveConsumer { config.particleShakeScale = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(tr("bhi.config.particle_medium_damage"), config.particleMediumDamage)
                .setMin(1.0)
                .setMax(100.0)
                .setDefaultValue(Defaults.PARTICLE_MEDIUM_DAMAGE)
                .setTooltip(tr("bhi.config.particle_medium_damage.tip"))
                .setSaveConsumer { config.particleMediumDamage = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(tr("bhi.config.particle_heavy_damage"), config.particleHeavyDamage)
                .setMin(1.0)
                .setMax(200.0)
                .setDefaultValue(Defaults.PARTICLE_HEAVY_DAMAGE)
                .setTooltip(tr("bhi.config.particle_heavy_damage.tip"))
                .setSaveConsumer { config.particleHeavyDamage = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(tr("bhi.config.container_shatter_enabled"), config.containerShatterEnabled)
                .setDefaultValue(Defaults.CONTAINER_SHATTER_ENABLED)
                .setTooltip(tr("bhi.config.container_shatter_enabled.tip"))
                .setSaveConsumer { config.containerShatterEnabled = it }
                .build(),
        )
        // 多重血条「上层」配色：最底层(第 1 排)恒为原版红心，第 2 排起依次取以下颜色，超出循环。
        for (index in config.tierColors.indices) {
            val rowLabel = index + 2
            head.addEntry(
                entry.startColorField(tr("bhi.config.tier_color", rowLabel), config.tierColors[index])
                    .setDefaultValue(Defaults.TIER_COLORS[index % Defaults.TIER_COLORS.size])
                    .setTooltip(tr("bhi.config.tier_color.tip", rowLabel))
                    .setSaveConsumer { config.tierColors[index] = it }
                    .build(),
            )
        }

        val panel = builder.getOrCreateCategory(tr("bhi.config.category.panel"))
        panel.addEntry(
            entry.startBooleanToggle(tr("bhi.config.panel_enabled"), config.panelEnabled)
                .setDefaultValue(Defaults.PANEL_ENABLED)
                .setSaveConsumer { config.panelEnabled = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(tr("bhi.config.panel_corner"), PanelCorner::class.java, config.panelCorner)
                .setDefaultValue(Defaults.PANEL_CORNER)
                .setEnumNameProvider { tr("bhi.config.enum.panel_corner.${it.name.lowercase()}") }
                .setSaveConsumer { config.panelCorner = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(tr("bhi.config.panel_frame_shape"), PanelFrameShape::class.java, config.panelFrameShape)
                .setDefaultValue(Defaults.PANEL_FRAME_SHAPE)
                .setEnumNameProvider { tr("bhi.config.enum.panel_frame_shape.${it.name.lowercase()}") }
                .setTooltip(tr("bhi.config.panel_frame_shape.tip"))
                .setSaveConsumer { config.panelFrameShape = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(tr("bhi.config.panel_theme"), PanelTheme::class.java, config.panelTheme)
                .setDefaultValue(Defaults.PANEL_THEME)
                .setEnumNameProvider { tr("bhi.config.enum.panel_theme.${it.name.lowercase()}") }
                .setTooltip(tr("bhi.config.panel_theme.tip"))
                .setSaveConsumer { config.panelTheme = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(tr("bhi.config.panel_bar_style"), PanelBarStyle::class.java, config.panelBarStyle)
                .setDefaultValue(Defaults.PANEL_BAR_STYLE)
                .setEnumNameProvider { tr("bhi.config.enum.panel_bar_style.${it.name.lowercase()}") }
                .setTooltip(tr("bhi.config.panel_bar_style.tip"))
                .setSaveConsumer { config.panelBarStyle = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(tr("bhi.config.panel_heart_highlight"), config.panelHeartHighlight)
                .setDefaultValue(Defaults.PANEL_HEART_HIGHLIGHT)
                .setTooltip(tr("bhi.config.panel_heart_highlight.tip"))
                .setSaveConsumer { config.panelHeartHighlight = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(tr("bhi.config.panel_show_effects"), config.panelShowEffects)
                .setDefaultValue(Defaults.PANEL_SHOW_EFFECTS)
                .setTooltip(tr("bhi.config.panel_show_effects.tip"))
                .setSaveConsumer { config.panelShowEffects = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(tr("bhi.config.panel_show_model"), config.panelShowModel)
                .setDefaultValue(Defaults.PANEL_SHOW_MODEL)
                .setSaveConsumer { config.panelShowModel = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(tr("bhi.config.panel_text_bold"), config.panelTextBold)
                .setDefaultValue(Defaults.PANEL_TEXT_BOLD)
                .setTooltip(tr("bhi.config.panel_text_bold.tip"))
                .setSaveConsumer { config.panelTextBold = it }
                .build(),
        )

        return builder.build()
    }
}
