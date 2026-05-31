package com.wjz.betterhealthindicator.client.render

import com.wjz.betterhealthindicator.config.HealthIndicatorConfig
import com.wjz.betterhealthindicator.config.HealthMode
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 爱心血条的布局与状态计算，是头顶渲染与掉血粒子的唯一真相源。
 *
 * 两者共用同一套槽位（位置 + 顶层填充 + 分层颜色），从而保证「掉落的那颗心」与「血条上那颗心」精确对齐。
 *
 * 注意坐标镜像：渲染走 billboard 的 scale(-x)，局部 +X 会映射到屏幕左侧。
 * 因此 [cxFor] 在 drainFromRight 时把逻辑 0（最后清空的满心）放到最大 cx，使其显示在屏幕最左，达成「从右往左扣」。
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
    )

    /** 一颗心的视觉位置与颜色，供掉血粒子按血量定位取色。 */
    class HeartRef(val cx: Float, val tier: HeartGraphics.HeartTier)

    /**
     * @param slots 槽位列表
     * @param multiplier >paletteSize 层时显示的 xN 倍数；为 0 表示不显示
     */
    class View(val slots: List<Slot>, val multiplier: Int)

    private fun isTiered(maxHealth: Float, config: HealthIndicatorConfig): Boolean =
        config.healthMode == HealthMode.ABSOLUTE && config.tieredHearts && maxHealth > LAYER_HP

    private fun flatHeartCount(maxHealth: Float, config: HealthIndicatorConfig): Int = when (config.healthMode) {
        HealthMode.RELATIVE -> config.relativeHeartCount.coerceIn(1, HEARTS_PER_ROW)
        HealthMode.ABSOLUTE -> ceil(maxHealth / HP_PER_HEART).toInt().coerceIn(1, HEARTS_PER_ROW)
    }

    /** 每颗心代表的血量。绝对分层恒为 2HP；其余按固定心数均摊。 */
    fun hpPerHeart(maxHealth: Float, config: HealthIndicatorConfig): Float =
        if (isTiered(maxHealth, config)) HP_PER_HEART else maxHealth / flatHeartCount(maxHealth, config)

    /**
     * 逻辑序号 → 槽位中心 X。逻辑 0 表示「最先填满 / 最后清空」的那颗心。
     * 因渲染镜像，drainFromRight 时逻辑 0 置于最大 cx（屏幕最左），右侧的高逻辑序号先清空。
     */
    fun cxFor(logical: Int, heartCount: Int, drainFromRight: Boolean): Float {
        val startX = -heartCount * SPACING / 2.0f
        val physical = if (drainFromRight) heartCount - 1 - logical else logical
        return startX + physical * SPACING + SPACING / 2.0f
    }

    fun compute(health: Float, maxHealth: Float, config: HealthIndicatorConfig): View =
        if (isTiered(maxHealth, config)) computeTiered(health, maxHealth, config) else computeFlat(health, maxHealth, config)

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
        val heartCount = flatHeartCount(maxHealth, config)
        val hpPerHeart = maxHealth / heartCount

        val slots = buildSlots(heartCount, config.drainFromRight) { logical ->
            val top = fillFor(health - logical * hpPerHeart, hpPerHeart)
            Triple(top, HeartGraphics.HeartTier.RED, null)
        }
        return View(slots, 0)
    }

    /**
     * 给定从 0 起算的血量值，返回该血量所落在的那颗心的视觉位置与颜色。
     * 用于掉血粒子：按绝对血量逐心采样，天然跨层取到正确的分层颜色。
     */
    fun heartRefAt(hp: Float, maxHealth: Float, config: HealthIndicatorConfig): HeartRef {
        if (isTiered(maxHealth, config)) {
            val layer = floor(hp / LAYER_HP).toInt().coerceAtLeast(0)
            val hpInLayer = hp - layer * LAYER_HP
            val logical = floor(hpInLayer / HP_PER_HEART).toInt().coerceIn(0, HEARTS_PER_ROW - 1)
            return HeartRef(cxFor(logical, HEARTS_PER_ROW, config.drainFromRight), HeartGraphics.HeartTier.byLayer(layer))
        }
        val heartCount = flatHeartCount(maxHealth, config)
        val hpPer = maxHealth / heartCount
        val logical = floor(hp / hpPer).toInt().coerceIn(0, heartCount - 1)
        return HeartRef(cxFor(logical, heartCount, config.drainFromRight), HeartGraphics.HeartTier.RED)
    }

    /** 顶层填充状态判定（也用于粒子的整/半判定）。 */
    fun fillFor(remainder: Float, hpPerHeart: Float): Top = when {
        remainder >= hpPerHeart * 0.75f -> Top.FULL
        remainder >= hpPerHeart * 0.25f -> Top.HALF
        else -> Top.NONE
    }

    private inline fun buildSlots(
        heartCount: Int,
        drainFromRight: Boolean,
        info: (logical: Int) -> Triple<Top, HeartGraphics.HeartTier, HeartGraphics.HeartTier?>,
    ): List<Slot> {
        val list = ArrayList<Slot>(heartCount)
        for (logical in 0 until heartCount) {
            val (top, topTier, baseTier) = info(logical)
            list.add(Slot(cxFor(logical, heartCount, drainFromRight), baseTier, top, topTier))
        }
        return list
    }
}
