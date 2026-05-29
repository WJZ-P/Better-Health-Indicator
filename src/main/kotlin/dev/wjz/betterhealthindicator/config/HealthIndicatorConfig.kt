package dev.wjz.betterhealthindicator.config

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

    val maxDistanceSquared: Double get() = maxDistance * maxDistance
}
