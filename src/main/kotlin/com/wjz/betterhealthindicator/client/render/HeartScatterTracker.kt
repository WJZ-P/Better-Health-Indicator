package com.wjz.betterhealthindicator.client.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * 头顶爱心「受击散开」追踪：受到伤害时让该实体每颗爱心从中心朝随机方向飞出（快）再缓慢飞回（慢），
 * 同时整颗心以中心为轴往左/右整体偏转一定角度（方向随机、角度随强度），呈现被击打后倾斜散开再回正的反馈。
 *
 * 按实体 id 独立计时，散开结束（时长耗尽）即清理。每颗心的随机方向与偏转侧由 (seed, index) 确定性
 * 派生，保证同一次散开内各帧稳定、不会逐帧乱跳。
 */
object HeartScatterTracker {
    private const val DURATION_MS = 300L // 单次散开总时长（飞出快、收回慢，超过即归零并清理）
    private const val PEAK_FRAC = 0.1f  // 飞出段时间占比（越小飞出越快、收回越慢）
    private val HALF_PI = (Math.PI / 2.0).toFloat()
    private val TWO_PI = (Math.PI * 2.0).toFloat()

    private val ZERO = floatArrayOf(0.0f, 0.0f, 0.0f)

    private class Burst(val startMs: Long, val amplitude: Float, val swing: Float, val seed: Int)

    private val bursts = HashMap<Int, Burst>()

    /**
     * 登记一次受击散开。
     * @param amplitude 径向最大偏移（局部爱心像素，<=0 忽略）。
     * @param swing 整颗心最大偏转角（弧度），越大倾斜越明显；随受击强度增大。
     */
    fun trigger(entityId: Int, amplitude: Float, swing: Float) {
        if (amplitude <= 0.0f) return
        //  这个魔法数是黄金分割素数，用来制作雪崩效应，使得种子足够随机
        val seed = ((entityId * 0x9E3779B1L) xor System.nanoTime()).toInt()
        bursts[entityId] = Burst(System.currentTimeMillis(), amplitude, swing, seed)
    }

    /**
     * 第 [index] 颗心当前的 [ox, oy, rot]：ox/oy 为局部爱心坐标位移（像素），rot 为整颗心绕中心的偏转角（弧度）。
     * 无进行中的散开返回全零。
     */
    fun offset(entityId: Int, index: Int): FloatArray {
        val burst = bursts[entityId] ?: return ZERO
        val elapsed = System.currentTimeMillis() - burst.startMs
        if (elapsed >= DURATION_MS) {
            bursts.remove(entityId)
            return ZERO
        }
        val progress = elapsed.toFloat() / DURATION_MS
        // 包络：飞出快（前 PEAK_FRAC 段 ease-out 冲到峰值），收回慢（其后 ease 缓降回 0）；位移与偏转共用。
        val env = if (progress < PEAK_FRAC) {
            sin((progress / PEAK_FRAC) * HALF_PI)
        } else {
            cos(((progress - PEAK_FRAC) / (1.0f - PEAK_FRAC)) * HALF_PI)
        }
        val a = burst.amplitude * env
        val angle = angleFor(burst.seed, index)
        // 整颗心绕中心整体偏转：偏转侧（左/右）随机，幅度随强度，随包络偏出快、回正慢（非震荡）。
        val rot = burst.swing * env * sideFor(burst.seed, index)
        return floatArrayOf(cos(angle) * a, sin(angle) * a, rot)
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

    /** 由 (seed, index) 确定性派生偏转侧：+1 向一侧、-1 向另一侧（取与 angleFor 不同的 hash 位，避免相关）。 */
    private fun sideFor(seed: Int, index: Int): Float {
        var h = (seed * 0x27D4EB2FL.toInt()) xor (index + 0x165667B1)
        h = h xor (h ushr 16)
        return if (h and 1 == 0) 1.0f else -1.0f
    }
}
