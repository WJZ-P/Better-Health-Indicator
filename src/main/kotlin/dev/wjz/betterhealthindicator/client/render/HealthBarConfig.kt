package dev.wjz.betterhealthindicator.client.render

/**
 * 血条显示的可调参数集中在此，方便日后接入配置界面或配置文件。
 */
data class HealthBarConfig(
    val maxDistance: Double = 48.0,
    val width: Float = 26.0f,
    val height: Float = 4.0f,
    val borderSize: Float = 1.0f,
    val yOffset: Double = 0.5,
    val scale: Float = 0.025f,
    val showSelf: Boolean = false,
    val showFullHealthEntities: Boolean = true,
    val foregroundAlpha: Int = 235,
    val backgroundColor: Int = 0xB0202020.toInt(),
    val borderColor: Int = 0xC0000000.toInt(),
    val debug: Boolean = true,
) {
    val maxDistanceSquared: Double get() = maxDistance * maxDistance
}
