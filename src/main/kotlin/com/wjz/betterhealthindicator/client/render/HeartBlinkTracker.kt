package com.wjz.betterhealthindicator.client.render

/**
 * 心形血条「血量变化白圈高亮」追踪，头顶血条与屏幕面板共用。
 *
 * 任意实体血量发生增减（扣血 / 回血）时触发约 1 秒的明灭高亮，还原原版受击 / 回血时心容器
 * 外圈闪白的反馈。按实体 id 独立计时；首次见到某实体仅登记基准、不触发，避免目标切换误闪。
 * 长时间未更新的条目惰性清理，防止 map 无限增长。
 */
object HeartBlinkTracker {
    private const val DURATION_MS = 1000L          // 单次高亮持续时长
    private const val INTERVAL_MS = 150L           // 白圈明灭翻转间隔（约 3 tick）
    private const val STALE_MS = 10_000L           // 超过此时长未更新即视为过期可清理
    private const val CLEANUP_INTERVAL_MS = 5_000L // 清理扫描的最小间隔

    private class Entry(var lastHealth: Float, var blinkUntilMs: Long, var lastSeenMs: Long)

    private val entries = HashMap<Int, Entry>()
    private var lastCleanupMs = 0L

    /**
     * 更新指定实体的血量并查询当前是否处于白圈「亮」相位。
     * 首次见到该实体仅登记基准、不触发；其后血量变化即刷新高亮计时。
     */
    fun update(entityId: Int, health: Float): Boolean {
        val now = System.currentTimeMillis()
        maybeCleanup(now)
        val entry = entries[entityId]
        if (entry == null) {
            entries[entityId] = Entry(health, 0L, now)
            return false
        }
        entry.lastSeenMs = now
        if (health != entry.lastHealth) {
            entry.blinkUntilMs = now + DURATION_MS
            entry.lastHealth = health
        }
        return now < entry.blinkUntilMs && ((entry.blinkUntilMs - now) / INTERVAL_MS) % 2L == 1L
    }

    private fun maybeCleanup(now: Long) {
        if (now - lastCleanupMs < CLEANUP_INTERVAL_MS) return
        lastCleanupMs = now
        entries.values.removeIf { now - it.lastSeenMs > STALE_MS }
    }
}
