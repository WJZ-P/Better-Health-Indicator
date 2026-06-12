package com.wjz.betterhealthindicator.client.gui

import com.wjz.betterhealthindicator.config.BarStyle
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.DisplayMode
import com.wjz.betterhealthindicator.config.HealthIndicatorConfig.Defaults
import com.wjz.betterhealthindicator.config.PanelBarStyle
import com.wjz.betterhealthindicator.config.PanelCorner
import com.wjz.betterhealthindicator.config.PanelFrameShape
import com.wjz.betterhealthindicator.config.PanelTheme
import com.wjz.betterhealthindicator.client.render.TintedHeartTextures
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
            .setSavingRunnable {
                ConfigManager.save()
                // 配色可能变了：丢弃旧染色贴图，下次渲染按新色重新烘焙。
                TintedHeartTextures.reset()
            }
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
        global.addEntry(
            entry.startBooleanToggle(Component.literal("追踪最近受击目标"), config.trackAttacked)
                .setDefaultValue(Defaults.TRACK_ATTACKED)
                .setTooltip(Component.literal("攻击某生物后，在有效期内即便准星移开，头顶血条（LOOKING_AT 策略）与屏幕面板仍持续显示它"))
                .setSaveConsumer { config.trackAttacked = it }
                .build(),
        )
        global.addEntry(
            entry.startDoubleField(Component.literal("受击追踪有效期（秒）"), config.attackTrackingSeconds)
                .setMin(1.0)
                .setMax(60.0)
                .setDefaultValue(Defaults.ATTACK_TRACKING_SECONDS)
                .setTooltip(Component.literal("受击目标持续显示的时长；攻击瞬间刷新计时，超时后不再追踪"))
                .setSaveConsumer { config.attackTrackingSeconds = it }
                .build(),
        )
        global.addEntry(
            entry.startBooleanToggle(Component.literal("隐藏原版伤害粒子"), config.hideVanillaDamageParticles)
                .setDefaultValue(Defaults.HIDE_VANILLA_DAMAGE_PARTICLES)
                .setTooltip(Component.literal("隐藏原版攻击命中时迸出的心形粒子，避免和mod自带的在视觉上冲突"))
                .setSaveConsumer { config.hideVanillaDamageParticles = it }
                .build(),
        )

        val head = builder.getOrCreateCategory(Component.literal("头顶血条"))

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
                .setTooltip(Component.literal("开启后在名字同一行追加「当前/上限」血量数值；关闭时多倍血条生物改在该位置显示「x倍数」"))
                .setSaveConsumer { config.showHealthText = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("详情显示在血条右侧"), config.detailBesideBar)
                .setDefaultValue(Defaults.DETAIL_BESIDE_BAR)
                .setTooltip(Component.literal("开启：血量条数(x N)/血量数值贴在爱心血条右侧（爱心仍居中）；关闭：用「|」接在名字行尾"))
                .setSaveConsumer { config.detailBesideBar = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("受击反馈特效"), config.headHeartHitEffect)
                .setDefaultValue(Defaults.HEAD_HEART_HIT_EFFECT)
                .setTooltip(Component.literal("心形血条受击/回血时：心容器外圈闪白高亮 + 整颗心散开晃动倾斜"))
                .setSaveConsumer { config.headHeartHitEffect = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(Component.literal("残血抖动阈值（血量比例）"), config.lowHealthShakeThreshold)
                .setMin(0.0)
                .setMax(1.0)
                .setDefaultValue(Defaults.LOW_HEALTH_SHAKE_THRESHOLD)
                .setTooltip(Component.literal("血量 ≤ 此比例时，整排爱心容器会颤抖；头顶与面板同时生效，0 为关闭"))
                .setSaveConsumer { config.lowHealthShakeThreshold = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("屏蔽原版名字标签"), config.hideVanillaNameTag)
                .setDefaultValue(Defaults.HIDE_VANILLA_NAME_TAG)
                .setTooltip(Component.literal("隐其原版浮空名字(玩家名、命名牌等)，避免名字重复与遮挡；未绘制血条的实体不受影响"))
                .setSaveConsumer { config.hideVanillaNameTag = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(Component.literal("名字字号倍率"), config.textScale)
                .setMin(0.3)
                .setMax(3.0)
                .setDefaultValue(Defaults.TEXT_SCALE)
                .setTooltip(Component.literal("名字文本相对原版名牌字号的倍率，1.0 即原版大小"))
                .setSaveConsumer { config.textScale = it }
                .build(),
        )
        head.addEntry(
            entry.startDoubleField(Component.literal("血量数值字号倍率"), config.healthTextScale)
                .setMin(0.3)
                .setMax(3.0)
                .setDefaultValue(Defaults.HEALTH_TEXT_SCALE)
                .setTooltip(Component.literal("血量数值相对原版名牌字号的倍率，可调小以与名字区分"))
                .setSaveConsumer { config.healthTextScale = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("文本加粗"), config.textBold)
                .setDefaultValue(Defaults.TEXT_BOLD)
                .setTooltip(Component.literal("开启后文本将会变成粗体"))
                .setSaveConsumer { config.textBold = it }
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
            entry.startDoubleField(Component.literal("血条高度偏移（格）"), config.yOffset)
                .setMin(-2.0)
                .setMax(3.0)
                .setDefaultValue(Defaults.Y_OFFSET)
                .setTooltip(Component.literal("血条在生物模型顶部之上的额外高度；0 即贴着模型最高点，正值上移、负值下移"))
                .setSaveConsumer { config.yOffset = it }
                .build(),
        )
        head.addEntry(
            entry.startBooleanToggle(Component.literal("受击掉血粒子"), config.damageParticlesEnabled)
                .setDefaultValue(Defaults.DAMAGE_PARTICLES_ENABLED)
                .setTooltip(Component.literal("生物受击掉血时，从对应爱心位置迸出掉落爱心粒子"))
                .setSaveConsumer { config.damageParticlesEnabled = it }
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
        head.addEntry(
            entry.startBooleanToggle(Component.literal("容器碎裂特效"), config.containerShatterEnabled)
                .setDefaultValue(Defaults.CONTAINER_SHATTER_ENABLED)
                .setTooltip(Component.literal("生物死亡时心形容器（背板）抖动并逐颗连锁碎裂；较酷炫，可关闭以免喧宾夺主"))
                .setSaveConsumer { config.containerShatterEnabled = it }
                .build(),
        )
        // 多重血条「上层」配色：最底层(第 1 排)恒为原版红心，第 2 排起依次取以下颜色，超出循环。
        // 由灰度模板运行时染色生成，主体=该色、边缘略暗、高光略亮；头顶与面板共用。
        for (index in config.tierColors.indices) {
            head.addEntry(
                entry.startColorField(Component.literal("多重血条颜色 ${index + 2}"), config.tierColors[index])
                    .setDefaultValue(Defaults.TIER_COLORS[index % Defaults.TIER_COLORS.size])
                    .setTooltip(Component.literal("第 ${index + 2} 排血条的主颜色（多重血条时生效）"))
                    .setSaveConsumer { config.tierColors[index] = it }
                    .build(),
            )
        }

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
            entry.startEnumSelector(Component.literal("模型边框形状"), PanelFrameShape::class.java, config.panelFrameShape)
                .setDefaultValue(Defaults.PANEL_FRAME_SHAPE)
                .setTooltip(Component.literal("面板内 3D 模型视口的边框：正方形或圆形"))
                .setSaveConsumer { config.panelFrameShape = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(Component.literal("面板主题"), PanelTheme::class.java, config.panelTheme)
                .setDefaultValue(Defaults.PANEL_THEME)
                .setTooltip(Component.literal("浅色：原版米白风；深色：半透明深色玻璃风"))
                .setSaveConsumer { config.panelTheme = it }
                .build(),
        )
        panel.addEntry(
            entry.startEnumSelector(Component.literal("血条样式"), PanelBarStyle::class.java, config.panelBarStyle)
                .setDefaultValue(Defaults.PANEL_BAR_STYLE)
                .setTooltip(Component.literal("纯色矩形血条，或原版爱心图标"))
                .setSaveConsumer { config.panelBarStyle = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("心形受击高亮"), config.panelHeartHighlight)
                .setDefaultValue(Defaults.PANEL_HEART_HIGHLIGHT)
                .setTooltip(Component.literal("心形样式下，目标扣血/回血时心容器外圈闪白（还原原版受伤反馈）"))
                .setSaveConsumer { config.panelHeartHighlight = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("显示状态效果"), config.panelShowEffects)
                .setDefaultValue(Defaults.PANEL_SHOW_EFFECTS)
                .setTooltip(Component.literal("血条下方排一行目标的药水效果图标。注意：原版不同步非玩家生物的效果给客户端，故仅在单机/自开局域网有效，进别人服务器对其它生物无效"))
                .setSaveConsumer { config.panelShowEffects = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("显示 3D 模型"), config.panelShowModel)
                .setDefaultValue(Defaults.PANEL_SHOW_MODEL)
                .setSaveConsumer { config.panelShowModel = it }
                .build(),
        )
        panel.addEntry(
            entry.startBooleanToggle(Component.literal("面板文本加粗"), config.panelTextBold)
                .setDefaultValue(Defaults.PANEL_TEXT_BOLD)
                .setTooltip(Component.literal("开启后，加粗面板内的字体"))
                .setSaveConsumer { config.panelTextBold = it }
                .build(),
        )

        return builder.build()
    }
}
