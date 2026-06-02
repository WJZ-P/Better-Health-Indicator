package com.wjz.betterhealthindicator.config

/** 头顶血条样式。 */
enum class BarStyle {
    /** 原版爱心图标（默认）。 */
    HEARTS,

    /** 纯色矩形血条。 */
    BAR,

    /** 纯数字血量（如 18/20）。 */
    NUMERIC,
}

/** 显示策略：决定哪些生物会被显示血量。 */
enum class DisplayMode {
    /** 范围内全部显示。 */
    ALWAYS,

    /** 仅显示准星对准的生物。 */
    LOOKING_AT,

    /** 仅显示当前在屏幕（视锥）内的生物。 */
    ON_SCREEN,
}

/** 屏幕血量面板所在角落。 */
enum class PanelCorner {
    TOP_LEFT,
    TOP_RIGHT,
}

/** 血量到爱心数量的换算方式。 */
enum class HealthMode {
    /** 绝对血量：严格 1 颗心 = 2HP，心数随血量增长；高血量启用分层异色（默认）。 */
    ABSOLUTE,

    /** 相对血量：固定心数，每颗心代表等比例血量。 */
    RELATIVE,
}

/**
 * 全部可调项集中于此，使用可变字段以便配置界面写入；通过 GSON 直接序列化为 JSON。
 */
class HealthIndicatorConfig {
    // 全局
    var enabled: Boolean = Defaults.ENABLED
    var maxDistance: Double = Defaults.MAX_DISTANCE
    var displayMode: DisplayMode = Defaults.DISPLAY_MODE

    // 头顶血条
    var headBarEnabled: Boolean = Defaults.HEAD_BAR_ENABLED
    var barStyle: BarStyle = Defaults.BAR_STYLE
    // 血量换算：绝对(默认) 严格 2HP/心；相对 固定心数按比例。
    var healthMode: HealthMode = Defaults.HEALTH_MODE
    // 掉血方向：true 从右往左扣（最右先空，原版一致）；false 从左往右扣。
    var drainFromRight: Boolean = Defaults.DRAIN_FROM_RIGHT
    // 绝对模式下，高于一排(20HP)的血量是否启用分层异色爱心。
    var tieredHearts: Boolean = Defaults.TIERED_HEARTS
    // 相对模式固定显示的爱心数量。
    var relativeHeartCount: Int = Defaults.RELATIVE_HEART_COUNT
    var showName: Boolean = Defaults.SHOW_NAME
    var showHealthText: Boolean = Defaults.SHOW_HEALTH_TEXT
    // 名字/血量文本相对原版名牌字号的倍率（自绘文本，1.0 = 原版大小）。
    var textScale: Double = Defaults.TEXT_SCALE
    var occludeBehindWalls: Boolean = Defaults.OCCLUDE_BEHIND_WALLS
    var showFullHealthEntities: Boolean = Defaults.SHOW_FULL_HEALTH_ENTITIES
    var showSelf: Boolean = Defaults.SHOW_SELF
    var barWidth: Float = Defaults.BAR_WIDTH
    var barHeight: Float = Defaults.BAR_HEIGHT
    var yOffset: Double = Defaults.Y_OFFSET
    var scale: Float = Defaults.SCALE
    var foregroundAlpha: Int = Defaults.FOREGROUND_ALPHA
    var backgroundColor: Int = Defaults.BACKGROUND_COLOR
    var borderColor: Int = Defaults.BORDER_COLOR

    // 生物受击掉血时，从对应爱心位置迸出的掉落爱心粒子总开关。
    var damageParticlesEnabled: Boolean = Defaults.DAMAGE_PARTICLES_ENABLED
    // 掉血爱心粒子：晃动/抖动幅度的全局倍率，以及伤害分档阈值（轻/中/重，越重越震撼）。
    var particleShakeScale: Double = Defaults.PARTICLE_SHAKE_SCALE
    // 伤害 ≥ 此值进入「中档」晃动；伤害 > particleHeavyDamage 进入「重档」（加大弹簧 + 抖动）。
    var particleMediumDamage: Double = Defaults.PARTICLE_MEDIUM_DAMAGE
    var particleHeavyDamage: Double = Defaults.PARTICLE_HEAVY_DAMAGE
    // 生物死亡时心形容器（背板）的抖动+逐颗连锁碎裂特效；较酷炫，可关闭以免喧宾夺主。
    var containerShatterEnabled: Boolean = Defaults.CONTAINER_SHATTER_ENABLED

    // 屏幕面板
    var panelEnabled: Boolean = Defaults.PANEL_ENABLED
    var panelCorner: PanelCorner = Defaults.PANEL_CORNER
    var panelShowModel: Boolean = Defaults.PANEL_SHOW_MODEL
    var panelScale: Float = Defaults.PANEL_SCALE
    // 受击兜底追踪：攻击某生物后，在有效期内即使没有其他可渲染目标，也以最低优先级兜底显示它。
    var panelTrackAttacked: Boolean = Defaults.PANEL_TRACK_ATTACKED
    // 受击目标的兜底有效期（秒）；攻击瞬间刷新计时，超时后不再兜底。
    var panelAttackTrackingSeconds: Double = Defaults.PANEL_ATTACK_TRACKING_SECONDS

    val maxDistanceSquared: Double get() = maxDistance * maxDistance

    /**
     * 全部配置项的默认值单一数据源：字段初始化与配置界面的 `setDefaultValue` 均引用这里，
     * 避免默认值在两处各写一遍而产生分歧。
     */
    object Defaults {
        const val ENABLED: Boolean = true
        const val MAX_DISTANCE: Double = 48.0
        val DISPLAY_MODE: DisplayMode = DisplayMode.ALWAYS

        const val HEAD_BAR_ENABLED: Boolean = true
        val BAR_STYLE: BarStyle = BarStyle.HEARTS
        val HEALTH_MODE: HealthMode = HealthMode.ABSOLUTE
        const val DRAIN_FROM_RIGHT: Boolean = true
        const val TIERED_HEARTS: Boolean = true
        const val RELATIVE_HEART_COUNT: Int = 10
        const val SHOW_NAME: Boolean = true
        const val SHOW_HEALTH_TEXT: Boolean = false
        const val TEXT_SCALE: Double = 1.0
        const val OCCLUDE_BEHIND_WALLS: Boolean = true
        const val SHOW_FULL_HEALTH_ENTITIES: Boolean = true
        const val SHOW_SELF: Boolean = false
        const val BAR_WIDTH: Float = 26.0f
        const val BAR_HEIGHT: Float = 4.0f
        const val Y_OFFSET: Double = 0.2
        const val SCALE: Float = 0.025f
        const val FOREGROUND_ALPHA: Int = 235
        val BACKGROUND_COLOR: Int = 0xB0202020.toInt()
        val BORDER_COLOR: Int = 0xC0000000.toInt()

        const val DAMAGE_PARTICLES_ENABLED: Boolean = true
        const val PARTICLE_SHAKE_SCALE: Double = 1.0
        const val PARTICLE_MEDIUM_DAMAGE: Double = 7.0
        const val PARTICLE_HEAVY_DAMAGE: Double = 10.0
        const val CONTAINER_SHATTER_ENABLED: Boolean = true

        const val PANEL_ENABLED: Boolean = true
        val PANEL_CORNER: PanelCorner = PanelCorner.TOP_LEFT
        const val PANEL_SHOW_MODEL: Boolean = true
        const val PANEL_SCALE: Float = 1.0f
        const val PANEL_TRACK_ATTACKED: Boolean = true
        const val PANEL_ATTACK_TRACKING_SECONDS: Double = 5.0
    }
}
