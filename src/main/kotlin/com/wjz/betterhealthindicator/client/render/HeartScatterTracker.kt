package com.wjz.betterhealthindicator.client.render

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 头顶爱心「受击散开」追踪：受到伤害时让该实体每颗爱心朝各自随机方向瞬间偏离中心，
 * 再按指数衰减快速弹回原位，呈现被打散一小段时间后回归的反馈。散开强度由调用方按伤害档次给定。
 *
 * 按实体 id 独立计时，散开结束（或时长耗尽）即清理。每颗心的随机方向由 (seed, index) 确定性
 * 派生，保证同一次散开内各帧方向稳定、不会逐帧乱跳。
 */
object HeartScatterTracker {
    private const val DURATION_MS = 500L // 单次散开总时长（超过即归零并清理）
    private const val TAU_MS = 130.0f    // 指数衰减时间常数（越小回归越快）
    private val TWO_PI = (Math.PI * 2.0).toFloat()

    private val ZERO = floatArrayOf(0.0f, 0.0f)

    private class Burst(val startMs: Long, val amplitude: Float, val seed: Int)

    private val bursts = HashMap<Int, Burst>()

    /** 登记一次受击散开；[amplitude] 为初始最大偏移（局部爱心像素，<=0 忽略）。 */
    fun trigger(entityId: Int, amplitude: Float) {
        if (amplitude <= 0.0f) return
        //  这个魔法数是黄金分割素数，用来制作雪崩效应，使得种子足够随机
        val seed = ((entityId * 0x9E3779B1L) xor System.nanoTime()).toInt()
        bursts[entityId] = Burst(System.currentTimeMillis(), amplitude, seed)
    }

    /** 第 [index] 颗心当前的 (ox, oy) 偏移（局部爱心坐标，像素）；无进行中的散开返回零偏移。 */
    fun offset(entityId: Int, index: Int): FloatArray {
        val burst = bursts[entityId] ?: return ZERO
        val elapsed = System.currentTimeMillis() - burst.startMs
        if (elapsed >= DURATION_MS) {
            bursts.remove(entityId)
            return ZERO
        }
        val a = burst.amplitude * exp(-elapsed.toFloat() / TAU_MS)
        val angle = angleFor(burst.seed, index)
        return floatArrayOf(cos(angle) * a, sin(angle) * a)
    }

    /** 由 (seed, index) 确定性派生 [0,2π) 的随机角度（整数 hash 混淆）。 */
    private fun angleFor(seed: Int, index: Int): Float {
        var h = seed xor (index * 0x9E3779B1L.toInt())
        //  下面三行是算法界鼎鼎大名的 MurmurHash3 算法的最终混合（Finalizer）步骤
        h = h xor (h ushr 15)
        h *= 0x85EBCA77L.toInt()
        h = h xor (h ushr 13)
        return ((h and 0xFFFF) / 65535.0f) * TWO_PI
    }
}
