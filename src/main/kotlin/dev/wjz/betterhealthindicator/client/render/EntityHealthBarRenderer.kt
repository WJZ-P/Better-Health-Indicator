package dev.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import dev.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import dev.wjz.betterhealthindicator.config.BarStyle
import dev.wjz.betterhealthindicator.config.ConfigManager
import dev.wjz.betterhealthindicator.config.HealthIndicatorConfig
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 生物头顶血量渲染。在 26.1 的 [LevelRenderEvents.COLLECT_SUBMITS] 阶段提交几何与文本：
 * - 图形（条 / 爱心）通过 [SubmitNodeCollector.submitCustomGeometry] 提交；
 * - 文本（名字 / 数值）通过 [SubmitNodeCollector.submitNameTag] 提交（原版浮空标签，自动朝向摄像机）。
 *
 * 同时检测生物掉血并交由 [HeartParticleManager] 生成掉落爱心粒子。
 */
object EntityHealthBarRenderer {
    private const val MAX_HEARTS = 10
    private const val HEART_SPACING = 8.0f
    private const val WHITE = -1
    private const val LINE_GAP = 0.30
    private const val NAME_TAG_BACKGROUND = true

    private val lastHealth = HashMap<Int, Float>()
    private val seenEntities = HashSet<Int>()

    fun register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(
            LevelRenderEvents.CollectSubmits { context -> collect(context) },
        )
        BetterHealthIndicatorLogger.info("Entity health bar renderer registered.")
    }

    private fun collect(context: LevelRenderContext) {
        val config = ConfigManager.config
        if (!config.enabled || !config.headBarEnabled) return

        val minecraft = Minecraft.getInstance()
        val tickProgress = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val frame = EntitySelector.buildFrame(minecraft, config, tickProgress) ?: return

        val poseStack = context.poseStack()
        val collector = context.submitNodeCollector()
        val cameraState = context.levelState().cameraRenderState

        HeartParticleManager.update()
        seenEntities.clear()

        for (entity in frame.level.entitiesForRendering()) {
            if (entity !is LivingEntity) continue
            seenEntities.add(entity.id)
            detectDamage(entity, frame, config)
            if (EntitySelector.shouldShow(entity, frame)) {
                submit(entity, frame, collector, poseStack, cameraState)
            }
        }
        lastHealth.keys.retainAll(seenEntities)

        if (!HeartParticleManager.isEmpty()) {
            HeartParticleManager.render(collector, poseStack, frame.cameraPosition, cameraState.orientation)
        }
    }

    /** 比较上一帧记录的血量，掉血时在血条世界位置生成下落爱心粒子。 */
    private fun detectDamage(entity: LivingEntity, frame: EntitySelector.Frame, config: HealthIndicatorConfig) {
        val current = entity.health
        val previous = lastHealth.put(entity.id, current) ?: return
        if (current >= previous - 0.01f || current <= 0.0f) return

        val cameraPosition = frame.cameraPosition
        if (entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) > config.maxDistanceSquared) return

        val maxHealth = entity.maxHealth
        if (maxHealth <= 0.0f) return
        val heartCount = ceil(maxHealth / 2.0f).toInt().coerceIn(1, MAX_HEARTS)
        val hpPerHeart = maxHealth / heartCount
        val heartsLost = ((previous - current) / hpPerHeart).roundToInt().coerceIn(1, heartCount)

        val position = entity.position()
        HeartParticleManager.spawn(
            position.x,
            position.y + entity.bbHeight + config.yOffset,
            position.z,
            heartsLost,
            false,
        )
    }

    private fun submit(
        entity: LivingEntity,
        frame: EntitySelector.Frame,
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        cameraState: CameraRenderState,
    ) {
        val config = frame.config
        val cameraPosition = frame.cameraPosition
        val position = entity.getPosition(frame.tickProgress)
        val base = Vec3(
            position.x - cameraPosition.x,
            position.y - cameraPosition.y,
            position.z - cameraPosition.z,
        )
        val barHeight = entity.bbHeight + config.yOffset
        val distanceSq = entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z)
        val healthRatio = (entity.health / entity.maxHealth).coerceIn(0.0f, 1.0f)

        when (config.barStyle) {
            BarStyle.BAR -> submitBar(collector, poseStack, base, barHeight, healthRatio, config)
            BarStyle.HEARTS -> submitHearts(collector, poseStack, base, barHeight, entity.health, entity.maxHealth, config.scale)
            BarStyle.NUMERIC -> {} // 数值样式仅显示文本
        }

        if (config.showName) {
            submitLabel(collector, poseStack, base, barHeight + LINE_GAP, entity.displayName, distanceSq, cameraState)
        }

        if (config.showHealthText || config.barStyle == BarStyle.NUMERIC) {
            val healthY = if (config.barStyle == BarStyle.NUMERIC) barHeight else barHeight - LINE_GAP
            val text = Component.literal("${ceil(entity.health).toInt()} / ${ceil(entity.maxHealth).toInt()}")
            submitLabel(collector, poseStack, base, healthY, text, distanceSq, cameraState)
        }
    }

    private fun submitLabel(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        base: Vec3,
        localY: Double,
        text: Component,
        distanceSq: Double,
        cameraState: CameraRenderState,
    ) {
        poseStack.pushPose()
        try {
            poseStack.translate(base.x, base.y, base.z)
            collector.submitNameTag(
                poseStack,
                Vec3(0.0, localY, 0.0),
                0,
                text,
                NAME_TAG_BACKGROUND,
                LightCoordsUtil.FULL_BRIGHT,
                distanceSq,
                cameraState,
            )
        } finally {
            poseStack.popPose()
        }
    }

    private fun submitBar(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        base: Vec3,
        height: Double,
        healthRatio: Float,
        config: HealthIndicatorConfig,
    ) {
        val width = config.barWidth
        val barHeight = config.barHeight
        val left = -width / 2.0f
        val right = width / 2.0f
        val top = -barHeight / 2.0f
        val bottom = barHeight / 2.0f
        val fillRight = left + width * healthRatio
        val border = 1.0f

        billboarded(poseStack, base, height, config.scale) {
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, consumer ->
                val m = pose.pose()
                solidQuad(consumer, m, left - border, top - border, right + border, bottom + border, config.borderColor)
                solidQuad(consumer, m, left, top, right, bottom, config.backgroundColor)
                solidQuad(consumer, m, left, top, fillRight, bottom, healthColor(healthRatio, config.foregroundAlpha))
            }
        }
    }

    private fun submitHearts(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        base: Vec3,
        height: Double,
        health: Float,
        maxHealth: Float,
        scale: Float,
    ) {
        val heartCount = ceil(maxHealth / 2.0f).toInt().coerceIn(1, MAX_HEARTS)
        val hpPerHeart = maxHealth / heartCount
        val totalWidth = heartCount * HEART_SPACING
        val startX = -totalWidth / 2.0f
        val halfSize = HeartGraphics.SIZE / 2.0f

        val fulls = ArrayList<Float>()
        val halves = ArrayList<Float>()
        val empties = ArrayList<Float>()
        for (i in 0 until heartCount) {
            val heartHealth = health - i * hpPerHeart
            val cx = startX + i * HEART_SPACING + HEART_SPACING / 2.0f
            when {
                heartHealth >= hpPerHeart * 0.75f -> fulls.add(cx)
                heartHealth >= hpPerHeart * 0.25f -> halves.add(cx)
                else -> empties.add(cx)
            }
        }

        billboarded(poseStack, base, height, scale) {
            drawHeartGroup(collector, poseStack, HeartGraphics.CONTAINER, empties, halfSize)
            drawHeartGroup(collector, poseStack, HeartGraphics.FULL, fulls, halfSize)
            drawHeartGroup(collector, poseStack, HeartGraphics.HALF, halves, halfSize)
        }
    }

    private fun drawHeartGroup(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        texture: net.minecraft.resources.Identifier,
        centers: List<Float>,
        halfSize: Float,
    ) {
        if (centers.isEmpty()) return
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture)) { pose, consumer ->
            val m = pose.pose()
            for (cx in centers) {
                HeartGraphics.quad(consumer, m, cx - halfSize, -halfSize, cx + halfSize, halfSize, WHITE)
            }
        }
    }

    private inline fun billboarded(poseStack: PoseStack, base: Vec3, height: Double, scale: Float, block: () -> Unit) {
        poseStack.pushPose()
        try {
            poseStack.translate(base.x, base.y + height, base.z)
            poseStack.mulPose(Minecraft.getInstance().gameRenderer.mainCamera.rotation())
            poseStack.scale(-scale, -scale, scale)
            block()
        } finally {
            poseStack.popPose()
        }
    }

    private fun solidQuad(consumer: VertexConsumer, matrix: Matrix4f, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        consumer.addVertex(matrix, left, bottom, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        consumer.addVertex(matrix, right, bottom, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        consumer.addVertex(matrix, right, top, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
        consumer.addVertex(matrix, left, top, 0.0f).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT)
    }

    /** 血量从满到空：绿 -> 黄 -> 红。 */
    private fun healthColor(ratio: Float, alpha: Int): Int {
        val red: Int
        val green: Int
        if (ratio >= 0.5f) {
            red = ((1.0f - ratio) * 2.0f * 255.0f).toInt()
            green = 255
        } else {
            red = 255
            green = (ratio * 2.0f * 255.0f).toInt()
        }
        return argb(alpha, red, green, 48)
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }
}
