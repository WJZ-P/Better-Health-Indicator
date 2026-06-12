package com.wjz.betterhealthindicator.client.render

import com.wjz.betterhealthindicator.config.HealthIndicatorConfig
import net.minecraft.client.Minecraft
import java.util.Random
import kotlin.math.sin

/**
 * 残血濒死反馈：当目标血量 ≤ 配置阈值（占最大血量比例）时，每颗爱心**独立、错峰**地垂直抖动，
 * 还原原版那种「血量见底、心脏乱跳」的观感。头顶 3D 血条与左上角面板共用此逻辑。
 *
 * 错峰机制（对齐用户描述）：每实体维护一组「起始延迟」计数（每颗心一个，初值随机 0~[DELAY_MAX] 刻）。
 * 每游戏刻把对应计数 -1；归零的那颗心进入「抖动」一小段，抖完再随机歇 0~[DELAY_MAX] 刻后重来。
 * 因此各心起跳时机被随机延迟打散，不再整排同起同停。
 */
object LowHealthShake {
    // 抖动幅度（爱心像素 / billboard 局部单位，与一颗心 9px 同尺度）。
    private const val AMPLITUDE_PX = 1.2f

    // 起始/休息延迟的最大刻数（随机 0~该值）。
    private const val DELAY_MAX = 5

    // 单次抖动持续刻数。
    private const val SHAKE_TICKS = 5

    // 抖动竖向振荡角速度（弧度/毫秒）：决定高频抖动的快慢。
    private const val VIB_OMEGA = 0.045

    private const val SLOTS = HeartLayout.HEARTS_PER_ROW

    private val random = Random()

    /** 每实体的逐颗抖动状态机（按游戏刻推进）。 */
    private class State {
        var lastTick: Long = Long.MIN_VALUE
        val shaking = BooleanArray(SLOTS)
        val timer = IntArray(SLOTS) { random.nextInt(DELAY_MAX + 1) } // 初始起始延迟
        val dir = FloatArray(SLOTS) // 本次抖动初始方向（+1/-1），使相邻心可先上或先下

        /** 推进一刻：递减计数，归零则在「抖动 / 休息」间切换。 */
        fun tick() {
            for (i in 0 until SLOTS) {
                if (--timer[i] > 0) continue
                if (shaking[i]) {
                    // 抖完进入休息：随机歇 0~DELAY_MAX 刻。
                    shaking[i] = false
                    timer[i] = random.nextInt(DELAY_MAX + 1)
                } else {
                    // 起始延迟耗尽：开始抖动一小段，随机初始方向。
                    shaking[i] = true
                    timer[i] = SHAKE_TICKS
                    dir[i] = if (random.nextBoolean()) 1.0f else -1.0f
                }
            }
        }
    }

    private val states = HashMap<Int, State>()

    /** 目标是否处于残血抖动区间（阈值 ≤ 0 视为关闭）。 */
    fun isLow(health: Float, maxHealth: Float, config: HealthIndicatorConfig): Boolean {
        val threshold = config.lowHealthShakeThreshold
        if (threshold <= 0.0) return false
        if (health <= 0.0f || maxHealth <= 0.0f) return false
        return health / maxHealth <= threshold.toFloat()
    }

    /**
     * 残血时返回某颗心当帧的垂直偏移；非残血返回 0（并清理其状态）。
     *
     * @param seed 实体标识（每实体独立状态）
     * @param index 该心在本行中的序号（0 起）
     */
    fun verticalOffset(seed: Int, index: Int, health: Float, maxHealth: Float, config: HealthIndicatorConfig): Float {
        if (!isLow(health, maxHealth, config)) {
            states.remove(seed)
            return 0.0f
        }
        if (index < 0 || index >= SLOTS) return 0.0f

        val state = states.getOrPut(seed) { State() }
        // 仅在游戏刻推进时驱动状态机；同一帧内多颗心调用只推进一次。
        val now = Minecraft.getInstance().level?.gameTime ?: 0L
        if (state.lastTick == Long.MIN_VALUE) {
            state.lastTick = now
        } else if (now > state.lastTick) {
            val delta = (now - state.lastTick).coerceAtMost(DELAY_MAX.toLong() * 4)
            repeat(delta.toInt()) { state.tick() }
            state.lastTick = now
        }

        if (!state.shaking[index]) return 0.0f
        // 抖动段内按真实时间做高频竖向振荡（平滑、连续，读作颤抖）。
        return (sin(System.currentTimeMillis() * VIB_OMEGA) * state.dir[index]).toFloat() * AMPLITUDE_PX
    }
}
