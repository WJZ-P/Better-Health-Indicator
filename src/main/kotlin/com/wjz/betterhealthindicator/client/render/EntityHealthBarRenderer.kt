package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.config.BarStyle
import com.wjz.betterhealthindicator.config.ConfigManager
import com.wjz.betterhealthindicator.config.HealthIndicatorConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.ceil

/**
 * 生物头顶血量渲染。在 26.1 的 [LevelRenderEvents.COLLECT_SUBMITS] 阶段提交几何与文本：
 * - 图形（条 / 爱心）通过 [SubmitNodeCollector.submitCustomGeometry] 提交；
 * - 文本（名字 / 数值）通过 [SubmitNodeCollector.submitNameTag] 提交（原版浮空标签，自动朝向摄像机）。
 *
 * 同时检测生物掉血并交由 [HeartParticleManager] 生成掉落爱心粒子。
 */
object EntityHealthBarRenderer {
    private const val WHITE = -1
    private const val LINE_GAP = 0.30
    private const val NAME_TAG_BACKGROUND = true

    private val lastHealth = HashMap<Int, Float>()
    private val seenEntities = HashSet<Int>()

    fun register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(
            LevelRenderEvents.CollectSubmits { context -> collect(context) },
        )
        // 掉血检测属于游戏状态采样，放在固定 20Hz 的客户端 tick，避免随帧率空转，并与渲染职责分离。
        ClientTickEvents.END_CLIENT_TICK.register(
            ClientTickEvents.EndTick { minecraft -> tickDamageDetection(minecraft) },
        )
        BetterHealthIndicatorLogger.info("Entity health bar renderer registered.")
    }

    /** 每客户端 tick 采样所有已加载生物的血量，检测掉血并生成爱心粒子。门控与头顶血条一致。 */
    private fun tickDamageDetection(minecraft: Minecraft) {
        val config = ConfigManager.config
        if (!config.enabled || !config.headBarEnabled) return

        val level = minecraft.level ?: return
        val cameraPosition = minecraft.gameRenderer.mainCamera.position()

        seenEntities.clear()
        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity) continue
            seenEntities.add(entity.id)
            detectDamage(entity, cameraPosition, config)
        }
        lastHealth.keys.retainAll(seenEntities)
    }

    private fun collect(context: LevelRenderContext) {
        val config = ConfigManager.config
        if (!config.enabled || !config.headBarEnabled) return

        val minecraft = Minecraft.getInstance()
        val tickProgress = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)

        val poseStack = context.poseStack()
        val collector = context.submitNodeCollector()
        val cameraState = context.levelState().cameraRenderState
        val frame = EntitySelector.buildFrame(minecraft, config, tickProgress, cameraState.cullFrustum) ?: return

        HeartParticleManager.update()

        for (entity in frame.level.entitiesForRendering()) {
            if (entity !is LivingEntity) continue
            if (EntitySelector.shouldShow(entity, frame)) {
                submit(entity, frame, collector, poseStack, cameraState)
            }
        }

        if (!HeartParticleManager.isEmpty()) {
            HeartParticleManager.render(collector, poseStack, frame.cameraPosition, cameraState.orientation)
        }
    }

    /**
     * 比较上一 tick 记录的血量；掉血时逐槽位比对血条布局，
     * 让每颗减少的爱心从它在血条上的确切世界位置、以它自身的颜色掉落。
     */
    private fun detectDamage(entity: LivingEntity, cameraPosition: Vec3, config: HealthIndicatorConfig) {
        val current = entity.health
        val previous = lastHealth.put(entity.id, current) ?: return
        if (current >= previous - 0.01f || current <= 0.0f) return

        if (entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) > config.maxDistanceSquared) return

        val maxHealth = entity.maxHealth
        if (maxHealth <= 0.0f) return

        val before = HeartLayout.compute(previous, maxHealth, config)
        val after = HeartLayout.compute(current, maxHealth, config)
        val slotCount = minOf(before.slots.size, after.slots.size)

        // 与 billboarded 相同的变换：局部 (cx,0) 经相机朝向旋转 + 缩放，得到该爱心的世界坐标。
        val cameraRotation = Minecraft.getInstance().gameRenderer.mainCamera.rotation()
        val height = entity.bbHeight + config.yOffset
        val position = entity.position()
        val scale = config.scale

        for (i in 0 until slotCount) {
            val slotBefore = before.slots[i]
            val lostHalves = slotBefore.topHalves - after.slots[i].topHalves
            if (lostHalves <= 0) continue

            val offset = Vector3f(-scale * slotBefore.cx, 0.0f, 0.0f)
            cameraRotation.transform(offset)
            val texture = if (lostHalves == 1) slotBefore.topTier.half else slotBefore.topTier.full
            HeartParticleManager.spawn(
                position.x + offset.x,
                position.y + height + offset.y,
                position.z + offset.z,
                texture,
            )
        }
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

        var heartMultiplier = 0
        when (config.barStyle) {
            BarStyle.BAR -> submitBar(collector, poseStack, base, barHeight, healthRatio, config)
            BarStyle.HEARTS -> heartMultiplier =
                submitHearts(collector, poseStack, base, barHeight, entity.health, entity.maxHealth, config)
            BarStyle.NUMERIC -> {} // 数值样式仅显示文本
        }

        if (heartMultiplier > 0) {
            // 超出调色板层数的高血量生物，在血条上方标注「共多少管血」。
            val text = Component.literal("x$heartMultiplier")
            submitLabel(collector, poseStack, base, barHeight + LINE_GAP * 2, text, distanceSq, cameraState)
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

    /** 提交头顶爱心血条，返回需要标注的「血量管数」倍数（0 表示无需标注）。 */
    private fun submitHearts(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        base: Vec3,
        height: Double,
        health: Float,
        maxHealth: Float,
        config: HealthIndicatorConfig,
    ): Int {
        val view = HeartLayout.compute(health, maxHealth, config)
        val halfSize = HeartGraphics.SIZE / 2.0f

        // 同贴图合并为一次几何提交；先底层后顶层，保证半心背后正确露出 container 或下层颜色。
        val groups = LinkedHashMap<Identifier, MutableList<Float>>()
        fun add(texture: Identifier, cx: Float) = groups.getOrPut(texture) { ArrayList() }.add(cx)

        for (slot in view.slots) {
            if (slot.top == HeartLayout.Top.FULL) continue // 满心完全遮住底层，省略
            add(slot.baseTier?.full ?: HeartGraphics.CONTAINER, slot.cx)
        }
        for (slot in view.slots) {
            when (slot.top) {
                HeartLayout.Top.FULL -> add(slot.topTier.full, slot.cx)
                HeartLayout.Top.HALF -> add(slot.topTier.half, slot.cx)
                HeartLayout.Top.NONE -> {}
            }
        }

        billboarded(poseStack, base, height, config.scale) {
            for ((texture, centers) in groups) {
                drawHeartGroup(collector, poseStack, texture, centers, halfSize)
            }
        }
        return view.multiplier
    }

    private fun drawHeartGroup(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        texture: Identifier,
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
