package com.wjz.betterhealthindicator.client.render

import kotlin.math.sin

/**
 * 头顶爱心「受击颤抖」追踪：受到伤害时让该实体每颗爱心在一小段时间内做高频连续抖动
 * （位移 + 整颗心左右偏转），振幅随时间衰减，呈现爱心容器「被打得颤抖、逐渐平息」的反馈。
 *
 * 按实体 id 独立计时，抖动结束（时长耗尽）即清理。每颗心的相位由 (seed, index) 确定性派生，
 * 使各颗心各自颤抖、互不同步，且同一次抖动内各帧连续稳定、不会逐帧乱跳。
 */
object HeartScatterTracker {
    private const val DURATION_MS = 320L // 单次颤抖总时长（振幅由满到 0 衰减，超过即归零并清理）
    private val TWO_PI = (Math.PI * 2.0).toFloat()

    // 抖动频率：整段时长内的完整振荡圈数（越大越高频）。x/y/rot 取互不相等的频率，
    // 使位移呈非直线的杂乱颤抖、偏转与位移也不同步，更像真实的「颤抖」而非规则摆动。
    private const val SHAKE_CYCLES_X = 0.5f
    private const val SHAKE_CYCLES_Y = 1.0f
    private const val SHAKE_CYCLES_ROT = 2.0f

    private val ZERO = floatArrayOf(0.0f, 0.0f, 0.0f)

    private class Burst(val startMs: Long, val amplitude: Float, val swing: Float, val seed: Int)

    private val bursts = HashMap<Int, Burst>()

    /**
     * 登记一次受击颤抖。
     * @param amplitude 颤抖位移峰值（局部爱心像素，<=0 忽略）。
     * @param swing 整颗心偏转角峰值（弧度），越大左右抖得越明显；随受击强度增大。
     */
    fun trigger(entityId: Int, amplitude: Float, swing: Float) {
        if (amplitude <= 0.0f) return
        //  这个魔法数是黄金分割素数，用来制作雪崩效应，使得种子足够随机
        val seed = ((entityId * 0x9E3779B1L) xor System.nanoTime()).toInt()
        bursts[entityId] = Burst(System.currentTimeMillis(), amplitude, swing, seed)
    }

    /**
     * 第 [index] 颗心当前的 [ox, oy, rot]：ox/oy 为局部爱心坐标位移（像素），rot 为整颗心绕中心的偏转角（弧度）。
     * 无进行中的颤抖返回全零。
     */
    fun offset(entityId: Int, index: Int): FloatArray {
        val burst = bursts[entityId] ?: return ZERO
        val elapsed = System.currentTimeMillis() - burst.startMs
        if (elapsed >= DURATION_MS) {
            bursts.remove(entityId)
            return ZERO
        }
        val t = elapsed.toFloat() / DURATION_MS
        // 振幅包络：线性衰减（起手最强，逐渐平息），在结尾精确归零、平滑收束。
        val env = 1.0f - t
        val amp = burst.amplitude * env
        // 每颗心独立相位，互不同步；x/y 频率不同 → 二维杂乱颤抖。
        val ox = amp * sin(TWO_PI * SHAKE_CYCLES_X * t + phase(burst.seed, index, 0))
        val oy = amp * sin(TWO_PI * SHAKE_CYCLES_Y * t + phase(burst.seed, index, 1))
        // 整颗心绕中心高频左右偏转，振幅同样随强度与衰减。
        val rot = burst.swing * env * sin(TWO_PI * SHAKE_CYCLES_ROT * t + phase(burst.seed, index, 2))
        return floatArrayOf(ox, oy, rot)
    }

    /** 由 (seed, index, salt) 确定性派生 [0,2π) 的相位（整数 hash 混淆，salt 用于解耦 x/y/rot 三路）。 */
    private fun phase(seed: Int, index: Int, salt: Int): Float {
        var h = seed xor (index * 0x9E3779B1L.toInt()) xor (salt * 0x85EBCA77L.toInt())
        //  下面三行是算法界鼎鼎大名的 MurmurHash3 算法的最终混合（Finalizer）步骤
        h = h xor (h ushr 15)
        h *= 0x85EBCA77L.toInt()
        h = h xor (h ushr 13)
        return ((h and 0xFFFF) / 65535.0f) * TWO_PI
    }
}
