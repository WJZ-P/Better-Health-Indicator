package com.wjz.betterhealthindicator.client.render

import com.wjz.betterhealthindicator.config.HealthIndicatorConfig
import com.wjz.betterhealthindicator.config.HealthMode
import kotlin.math.ceil

/**
 * 爱心血条的布局与状态计算，是头顶渲染与掉血粒子的唯一真相源。
 *
 * 两者共用同一套槽位（位置 + 顶层填充 + 分层颜色），从而保证「掉落的那颗心」与「血条上那颗心」精确对齐。
 */
object HeartLayout {
    const val HEARTS_PER_ROW = 10
    const val HP_PER_HEART = 2.0f

    /** 单颗心的横向间距（局部 billboard 坐标，略小于 9 的贴图尺寸以便并排略叠）。 */
    const val SPACING = 8.0f

    /** 一整排爱心代表的血量（绝对模式分层的层高）。 */
    const val LAYER_HP = HEARTS_PER_ROW * HP_PER_HEART

    /** 顶层填充状态。 */
    enum class Top { NONE, HALF, FULL }

    /**
     * 单个槽位：
     * @param cx 槽位中心 X（局部 billboard 坐标）
     * @param baseTier 底层（被揭示出的下层满心颜色）；null 表示画黑色 container 底
     * @param top 顶层（当前层颜色）的填充状态
     * @param topTier 顶层颜色
     */
    class Slot(
        val cx: Float,
        val baseTier: HeartGraphics.HeartTier?,
        val top: Top,
        val topTier: HeartGraphics.HeartTier,
    ) {
        /** 顶层填充的半心数：FULL=2、HALF=1、NONE=0。用于粒子差异比较。 */
        val topHalves: Int get() = when (top) {
            Top.FULL -> 2
            Top.HALF -> 1
            Top.NONE -> 0
        }
    }

    /**
     * @param slots 槽位列表，下标即逻辑序号（与掉血方向无关，便于前后帧逐位比较）
     * @param multiplier >paletteSize 层时显示的 xN 倍数；为 0 表示不显示
     */
    class View(val slots: List<Slot>, val multiplier: Int)

    fun compute(health: Float, maxHealth: Float, config: HealthIndicatorConfig): View {
        val tiered = config.healthMode == HealthMode.ABSOLUTE && config.tieredHearts && maxHealth > LAYER_HP
        return if (tiered) computeTiered(health, maxHealth, config) else computeFlat(health, maxHealth, config)
    }

    /** 分层异色：固定一排，每满 20HP 进入下一层颜色，空出的顶层揭示下一层满心而非黑底。 */
    private fun computeTiered(health: Float, maxHealth: Float, config: HealthIndicatorConfig): View {
        val totalLayers = ceil(maxHealth / LAYER_HP).toInt().coerceAtLeast(1)
        val currentLayer = (ceil(health / LAYER_HP).toInt() - 1).coerceAtLeast(0)
        val hpInTop = health - currentLayer * LAYER_HP
        val topTier = HeartGraphics.HeartTier.byLayer(currentLayer)
        val baseTier = if (currentLayer > 0) HeartGraphics.HeartTier.byLayer(currentLayer - 1) else null
        val paletteSize = HeartGraphics.HeartTier.entries.size
        val multiplier = if (totalLayers > paletteSize) totalLayers else 0

        val slots = buildSlots(HEARTS_PER_ROW, config.drainFromRight) { logical ->
            val top = fillFor(hpInTop - logical * HP_PER_HEART, HP_PER_HEART)
            Triple(top, topTier, baseTier)
        }
        return View(slots, multiplier)
    }

    /** 不分层：相对模式按固定心数等比例；绝对模式按 2HP/心（>20HP 时压缩）。单层红心、黑底。 */
    private fun computeFlat(health: Float, maxHealth: Float, config: HealthIndicatorConfig): View {
        val heartCount = when (config.healthMode) {
            HealthMode.RELATIVE -> config.relativeHeartCount.coerceIn(1, HEARTS_PER_ROW)
            HealthMode.ABSOLUTE -> ceil(maxHealth / HP_PER_HEART).toInt().coerceIn(1, HEARTS_PER_ROW)
        }
        val hpPerHeart = maxHealth / heartCount

        val slots = buildSlots(heartCount, config.drainFromRight) { logical ->
            val top = fillFor(health - logical * hpPerHeart, hpPerHeart)
            Triple(top, HeartGraphics.HeartTier.RED, null)
        }
        return View(slots, 0)
    }

    /** 按某颗心可用血量与每心血量，判定其顶层填充状态。 */
    private fun fillFor(remainder: Float, hpPerHeart: Float): Top = when {
        remainder >= hpPerHeart * 0.75f -> Top.FULL
        remainder >= hpPerHeart * 0.25f -> Top.HALF
        else -> Top.NONE
    }

    /**
     * 生成槽位。逻辑序号 0 表示「最先填满 / 最后清空」的那颗心。
     * drainFromRight=true 时逻辑 0 在最左（最右先空，原版一致）；false 时逻辑 0 在最右。
     */
    private inline fun buildSlots(
        heartCount: Int,
        drainFromRight: Boolean,
        info: (logical: Int) -> Triple<Top, HeartGraphics.HeartTier, HeartGraphics.HeartTier?>,
    ): List<Slot> {
        val totalWidth = heartCount * SPACING
        val startX = -totalWidth / 2.0f
        val half = SPACING / 2.0f
        val list = ArrayList<Slot>(heartCount)
        for (logical in 0 until heartCount) {
            val physical = if (drainFromRight) logical else heartCount - 1 - logical
            val cx = startX + physical * SPACING + half
            val (top, topTier, baseTier) = info(logical)
            list.add(Slot(cx, baseTier, top, topTier))
        }
        return list
    }
}
