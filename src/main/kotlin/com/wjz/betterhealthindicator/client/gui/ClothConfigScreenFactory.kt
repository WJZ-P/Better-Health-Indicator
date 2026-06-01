package com.wjz.betterhealthindicator.client.gui

import com.wjz.betterhealthindicator.config.BarStyle
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.DisplayMode
import com.wjz.betterhealthindicator.config.HealthIndicatorConfig.Defaults
import com.wjz.betterhealthindicator.config.PanelCorner
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 用 Cloth Config 构建分类设置页（全局 / 头顶血条 / 屏幕面板），保存时写回 [ConfigManager]。
 */
object ClothConfigScreenFactory {
    fun create(parent: Screen): Screen {
        val config = ConfigManager.config
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Better Health Indicator"))
            .setSavingRunnable { ConfigManager.save() }
        val entry = builder.entryBuilder()

        val global = builder.getOrCreateCategory(Component.literal("全局"))
        global.addEntry(
            entry.startBooleanToggle(Component.literal("启用模组"), config.enabled)
                .setDefaultValue(Defaults.ENABLED)
                .setSaveConsumer { config.enabled = it }
                .build(),
        )
        global.addEntry(
            entry.startDoubleField(Component.literal("最大显示距离（格）"), config.maxDistance)
                .setMin(1.0)
                .setMax(256.0)
                .setDefaultValue(Defaults.MAX_DISTANCE)
                .setSaveConsumer { config.maxDistance = it }
                .build(),
        )
        global.addEntry(
            entry.startEnumSelector(Component.literal("显示策略"), DisplayMode::class.java, config.displayMode)
                .setDefaultValue(Defaults.DISPLAY_MODE)
                .setSaveConsumer { config.displayMode = it }
                .build(),
        )

        val head = builder.getOrCreateCategory(Component.literal("头顶血条"))
        head.addEntry(
            entry.startBooleanToggle(Component.literal("启用头顶血条"), config.headBarEnabled)
                .setDefaultValue(Defaults.HEAD_BAR_ENABLED)
                .setSaveConsumer { config.headBarEnabled = it }
                .build(),
        )
        head.addEntry(
            entry.startEnumSelector(Component.literal("血条样式"), BarStyle::class.java, config.barStyle)
                .setDefaultValue(Defaults.BAR_STYLE)
                .setSaveConsumer { config.barStyle = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示名字"), config.showName)
                .setDefaultValue(Defaults.SHOW_NAME)
                .setSaveConsumer { config.showName = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示血量数值"), config.showHealthText)
                .setDefaultValue(Defaults.SHOW_HEALTH_TEXT)
                .setSaveConsumer { config.showHealthText = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("被墙体遮挡时隐藏"), config.occludeBehindWalls)
                .setDefaultValue(Defaults.OCCLUDE_BEHIND_WALLS)
                .setSaveConsumer { config.occludeBehindWalls = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示满血生物"), config.showFullHealthEntities)
                .setDefaultValue(Defaults.SHOW_FULL_HEALTH_ENTITIES)
                .setSaveConsumer { config.showFullHealthEntities = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示玩家自己"), config.showSelf)
                .setDefaultValue(Defaults.SHOW_SELF)
                .setSaveConsumer { config.showSelf = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(Component.literal("掉血粒子抖动幅度倍率"), config.particleShakeScale)
                .setMin(0.0)
                .setMax(3.0)
                .setDefaultValue(Defaults.PARTICLE_SHAKE_SCALE)
                .setTooltip(Component.literal("掉血爱心晃动/抖动的整体强度，0 为关闭晃动"))
                .setSaveConsumer { config.particleShakeScale = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(Component.literal("粒子中档伤害阈值（HP）"), config.particleMediumDamage)
                .setMin(1.0)
                .setMax(100.0)
                .setDefaultValue(Defaults.PARTICLE_MEDIUM_DAMAGE)
                .setTooltip(Component.literal("单次伤害 ≥ 此值进入中等幅度晃动"))
                .setSaveConsumer { config.particleMediumDamage = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(Component.literal("粒子重档伤害阈值（HP）"), config.particleHeavyDamage)
                .setMin(1.0)
                .setMax(200.0)
                .setDefaultValue(Defaults.PARTICLE_HEAVY_DAMAGE)
                .setTooltip(Component.literal("单次伤害 > 此值进入重档：加大弹簧并叠加抖动"))
                .setSaveConsumer { config.particleHeavyDamage = it }
                .build(),
        )

        val panel = builder.getOrCreateCategory(Component.literal("屏幕面板"))
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("启用屏幕面板"), config.panelEnabled)
                .setDefaultValue(Defaults.PANEL_ENABLED)
                .setSaveConsumer { config.panelEnabled = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(Component.literal("面板位置"), PanelCorner::class.java, config.panelCorner)
                .setDefaultValue(Defaults.PANEL_CORNER)
                .setSaveConsumer { config.panelCorner = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("显示 3D 模型"), config.panelShowModel)
                .setDefaultValue(Defaults.PANEL_SHOW_MODEL)
                .setSaveConsumer { config.panelShowModel = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("追踪最近受击目标（兜底）"), config.panelTrackAttacked)
                .setDefaultValue(Defaults.PANEL_TRACK_ATTACKED)
                .setSaveConsumer { config.panelTrackAttacked = it }
                .build(),
        )
        panel.addEntry(
            entry.startDoubleField(Component.literal("受击追踪有效期（秒）"), config.panelAttackTrackingSeconds)
                .setMin(1.0)
                .setMax(60.0)
                .setDefaultValue(Defaults.PANEL_ATTACK_TRACKING_SECONDS)
                .setSaveConsumer { config.panelAttackTrackingSeconds = it }
                .build(),
        )

        return builder.build()
    }
}
