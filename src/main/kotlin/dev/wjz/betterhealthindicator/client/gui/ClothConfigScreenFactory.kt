package dev.wjz.betterhealthindicator.client.gui

import dev.wjz.betterhealthindicator.config.BarStyle
import dev.wjz.betterhealthindicator.config.ConfigManager
import dev.wjz.betterhealthindicator.config.DisplayMode
import dev.wjz.betterhealthindicator.config.PanelCorner
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
                .setDefaultValue(true)
                .setSaveConsumer { config.enabled = it }
                .build(),
        )
        global.addEntry(
            entry.startDoubleField(Component.literal("最大显示距离（格）"), config.maxDistance)
                .setMin(1.0)
                .setMax(256.0)
                .setDefaultValue(48.0)
                .setSaveConsumer { config.maxDistance = it }
                .build(),
        )
        global.addEntry(
            entry.startEnumSelector(Component.literal("显示策略"), DisplayMode::class.java, config.displayMode)
                .setDefaultValue(DisplayMode.ALWAYS)
                .setSaveConsumer { config.displayMode = it }
                .build(),
        )

        val head = builder.getOrCreateCategory(Component.literal("头顶血条"))
        head.addEntry(
            entry.startBooleanToggle(Component.literal("启用头顶血条"), config.headBarEnabled)
                .setDefaultValue(true)
                .setSaveConsumer { config.headBarEnabled = it }
                .build(),
        )
        head.addEntry(
            entry.startEnumSelector(Component.literal("血条样式"), BarStyle::class.java, config.barStyle)
                .setDefaultValue(BarStyle.HEARTS)
                .setSaveConsumer { config.barStyle = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示名字"), config.showName)
                .setDefaultValue(true)
                .setSaveConsumer { config.showName = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示血量数值"), config.showHealthText)
                .setDefaultValue(true)
                .setSaveConsumer { config.showHealthText = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("被墙体遮挡时隐藏"), config.occludeBehindWalls)
                .setDefaultValue(true)
                .setSaveConsumer { config.occludeBehindWalls = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示满血生物"), config.showFullHealthEntities)
                .setDefaultValue(true)
                .setSaveConsumer { config.showFullHealthEntities = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("显示玩家自己"), config.showSelf)
                .setDefaultValue(false)
                .setSaveConsumer { config.showSelf = it }
                .build(),
        )

        val panel = builder.getOrCreateCategory(Component.literal("屏幕面板"))
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("启用屏幕面板"), config.panelEnabled)
                .setDefaultValue(true)
                .setSaveConsumer { config.panelEnabled = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(Component.literal("面板位置"), PanelCorner::class.java, config.panelCorner)
                .setDefaultValue(PanelCorner.TOP_LEFT)
                .setSaveConsumer { config.panelCorner = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("显示 3D 模型"), config.panelShowModel)
                .setDefaultValue(true)
                .setSaveConsumer { config.panelShowModel = it }
                .build(),
        )

        return builder.build()
    }
}
