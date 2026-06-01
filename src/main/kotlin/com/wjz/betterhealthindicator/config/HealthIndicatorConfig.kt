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
    var enabled: Boolean = true
    var maxDistance: Double = 48.0
    var displayMode: DisplayMode = DisplayMode.ALWAYS

    // 头顶血条
    var headBarEnabled: Boolean = true
    var barStyle: BarStyle = BarStyle.HEARTS
    // 血量换算：绝对(默认) 严格 2HP/心；相对 固定心数按比例。
    var healthMode: HealthMode = HealthMode.ABSOLUTE
    // 掉血方向：true 从右往左扣（最右先空，原版一致）；false 从左往右扣。
    var drainFromRight: Boolean = true
    // 绝对模式下，高于一排(20HP)的血量是否启用分层异色爱心。
    var tieredHearts: Boolean = true
    // 相对模式固定显示的爱心数量。
    var relativeHeartCount: Int = 10
    var showName: Boolean = true
    var showHealthText: Boolean = true
    var occludeBehindWalls: Boolean = true
    var showFullHealthEntities: Boolean = true
    var showSelf: Boolean = false
    var barWidth: Float = 26.0f
    var barHeight: Float = 4.0f
    var yOffset: Double = 0.5
    var scale: Float = 0.025f
    var foregroundAlpha: Int = 235
    var backgroundColor: Int = 0xB0202020.toInt()
    var borderColor: Int = 0xC0000000.toInt()

    // 屏幕面板
    var panelEnabled: Boolean = true
    var panelCorner: PanelCorner = PanelCorner.TOP_LEFT
    var panelShowModel: Boolean = true
    var panelScale: Float = 1.0f
    // 受击兜底追踪：攻击某生物后，在有效期内即使没有其他可渲染目标，也以最低优先级兜底显示它。
    var panelTrackAttacked: Boolean = true
    // 受击目标的兜底有效期（秒）；攻击瞬间刷新计时，超时后不再兜底。
    var panelAttackTrackingSeconds: Double = 5.0

    val maxDistanceSquared: Double get() = maxDistance * maxDistance
}
