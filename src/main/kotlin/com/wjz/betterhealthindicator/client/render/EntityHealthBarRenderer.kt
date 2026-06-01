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
import kotlin.math.floor

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
    private const val MAX_PARTICLE_BURST = 20

    // 爱心相对 container 朝相机方向的“深度偏移量”，按世界单位/每格距离取值（模拟 polygon offset）。
    // 固定的局部 z 偏移会让偏离屏幕中心的心产生横向投影位移（越靠边越大 → 整排被剪切成斜的，即“歪”）；
    // 让世界偏移随距离成正比后，屏幕上的横向剪切恒定且亚像素（看不出歪），而深度差随距离增大，远处也不会 z-fighting。
    // 仅用于打破与 container 的共面平局，故取很小的值即可。
    private const val HEART_DEPTH_BIAS = 0.0015

    private val lastHealth = HashMap<Int, Float>()
    private val seenEntities = HashSet<Int>()

    // 本 tick 检测到的待生成粒子：仅记录爱心局部信息，世界坐标推迟到渲染帧解析（见 flushPendingSpawns）。
    private class PendingSpawn(
        val entity: LivingEntity,
        val cx: Float,
        val texture: Identifier,
        val flipU: Boolean,
        val style: HeartParticleManager.ParticleStyle,
    )

    private val pendingSpawns = ArrayList<PendingSpawn>()

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

        // 与原版粒子一致，物理按客户端 tick 定步推进；世界冻结（/tick freeze）时不推进。
        if (level.tickRateManager().runsNormally()) {
            HeartParticleManager.tick()
        }
    }

    private fun collect(context: LevelRenderContext) {
        val config = ConfigManager.config
        if (!config.enabled || !config.headBarEnabled) return

        val minecraft = Minecraft.getInstance()
        val tickProgress = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)

        // 在渲染帧解析本 tick 的待生成粒子，确保与血条爱心同基准、同帧、像素级重合（须先于可能的提前返回执行，避免堆积）。
        flushPendingSpawns(minecraft, config, tickProgress)

        val poseStack = context.poseStack()
        val collector = context.submitNodeCollector()
        val cameraState = context.levelState().cameraRenderState
        val frame = EntitySelector.buildFrame(minecraft, config, tickProgress, cameraState.cullFrustum) ?: return

        for (entity in frame.level.entitiesForRendering()) {
            if (entity !is LivingEntity) continue
            if (EntitySelector.shouldShow(entity, frame)) {
                submit(entity, frame, collector, poseStack, cameraState)
            }
        }

        if (!HeartParticleManager.isEmpty()) {
            HeartParticleManager.render(collector, poseStack, frame.cameraPosition, cameraState.orientation, tickProgress)
        }
    }

    /**
     * 比较上一 tick 记录的血量；掉血时按「绝对血量逐心」遍历受影响的爱心，
     * 让每颗减少的心从它在血条上的确切世界位置、以它自身（分层）的颜色掉落。
     *
     * 逐心（而非逐槽位）遍历可天然跨越分层边界：每颗心按其所在血量区间采样颜色，
     * 因此即便一次伤害跨越两种颜色，也能正确生成对应颜色的粒子。
     */
    private fun detectDamage(entity: LivingEntity, cameraPosition: Vec3, config: HealthIndicatorConfig) {
        val current = entity.health
        val previous = lastHealth.put(entity.id, current) ?: return
        if (current >= previous - 0.01f || current <= 0.0f) return

        if (entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) > config.maxDistanceSquared) return

        val maxHealth = entity.maxHealth
        if (maxHealth <= 0.0f) return

        val hpPerHeart = HeartLayout.hpPerHeart(maxHealth, config)
        if (hpPerHeart <= 0.0f) return

        // 本次总伤害决定整批粒子的掉落档次（轻/中/重）。
        val style = HeartParticleManager.styleFor(
            previous - current,
            config.particleMediumDamage.toFloat(),
            config.particleHeavyDamage.toFloat(),
            config.particleShakeScale.toFloat(),
        )

        // 遍历血量区间内被触及的每颗心：心 k 占据 [k*hpPerHeart, (k+1)*hpPerHeart)。
        val firstHeart = floor(current / hpPerHeart).toInt().coerceAtLeast(0)
        val lastHeart = ceil(previous / hpPerHeart).toInt() - 1
        var spawned = 0
        for (k in firstHeart..lastHeart) {
            if (spawned >= MAX_PARTICLE_BURST) break
            val heartBottom = k * hpPerHeart
            val beforeFill = (previous - heartBottom).coerceIn(0.0f, hpPerHeart)
            val afterFill = (current - heartBottom).coerceIn(0.0f, hpPerHeart)
            val afterHalves = halvesOf(afterFill, hpPerHeart)
            val lostHalves = halvesOf(beforeFill, hpPerHeart) - afterHalves
            if (lostHalves <= 0) continue

            val ref = HeartLayout.heartRefAt(heartBottom + hpPerHeart * 0.5f, maxHealth, config)
            val isHalf = lostHalves < 2
            val texture = if (isHalf) ref.tier.half else ref.tier.full
            // 掉落的是"被打掉的那半边"，与血条上保留的半心相反。
            // drainFromRight 下：满→半 掉右半(flipU=false)，半→空 掉左半(flipU=true)。
            val flipU = isHalf && (if (config.drainFromRight) afterHalves == 0 else afterHalves == 1)
            // 仅登记“待生成”，世界坐标推迟到渲染帧按血条爱心的同一基准解析，确保像素级重合、无缝衔接。
            pendingSpawns.add(PendingSpawn(entity, ref.cx, texture, flipU, style))
            spawned++
        }
    }

    /**
     * 把本 tick 登记的待生成粒子，按与血条爱心**完全相同的基准**解析为世界坐标后真正生成。
     *
     * 在渲染帧调用：复用血条爱心的插值位置 [LivingEntity.getPosition] 与同一相机朝向、缩放、高度，
     * 使粒子诞生位置与血条上那颗爱心像素级重合，且与掉血处于同一帧、无缝衔接。
     */
    private fun flushPendingSpawns(minecraft: Minecraft, config: HealthIndicatorConfig, tickProgress: Float) {
        if (pendingSpawns.isEmpty()) return
        val rotation = minecraft.gameRenderer.mainCamera.rotation()
        for (pending in pendingSpawns) {
            val entity = pending.entity
            val position = entity.getPosition(tickProgress)
            val height = entity.bbHeight + config.yOffset
            val offset = Vector3f(-config.scale * pending.cx, 0.0f, 0.0f)
            rotation.transform(offset)
            HeartParticleManager.spawn(
                position.x + offset.x,
                position.y + height + offset.y,
                position.z + offset.z,
                pending.texture,
                pending.flipU,
                pending.style,
            )
        }
        pendingSpawns.clear()
    }

    /** 某颗心当前填充对应的半心数（0/1/2），阈值与渲染填充判定保持一致。 */
    private fun halvesOf(fill: Float, hpPerHeart: Float): Int = when (HeartLayout.fillFor(fill, hpPerHeart)) {
        HeartLayout.Top.FULL -> 2
        HeartLayout.Top.HALF -> 1
        HeartLayout.Top.NONE -> 0
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

        billboard(poseStack, base, height, config.scale) {
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

        // 只有两层：container 背板 + 每槽位“自己算出的那一张爱心”，避免把所有层无脑叠上去（更省、且无多色重影）。
        // 跨贴图是独立 submitCustomGeometry 调用、绘制顺序不保证，故仍给爱心层一个比 container 更靠近相机的深度，
        // 由深度测试稳定压在 container 之上（仅 1 步，近距离视差≈黑色描边，不会再像三层那样糊成一片）。
        val containerLayer = LinkedHashMap<Identifier, MutableList<Float>>()
        val heartLayer = LinkedHashMap<Identifier, MutableList<Float>>()
        // 边界半心若有下层颜色：需在半心下垫一张“下层满心”露出空缺侧颜色，单独再高一步避免与半心共面。
        val halfRevealTop = LinkedHashMap<Identifier, MutableList<Float>>()
        fun add(map: LinkedHashMap<Identifier, MutableList<Float>>, texture: Identifier, cx: Float) =
            map.getOrPut(texture) { ArrayList() }.add(cx)

        for (slot in view.slots) {
            add(containerLayer, HeartGraphics.CONTAINER, slot.cx)
            when (slot.top) {
                // 满心：直接画当前层满心（不再叠下层色，下层被完全遮挡、画了纯属浪费且会重影）。
                HeartLayout.Top.FULL -> add(heartLayer, slot.topTier.full, slot.cx)
                // 空缺：有下层则露出下层满心颜色；无下层就只剩 container（黑底）。
                HeartLayout.Top.NONE -> slot.baseTier?.let { add(heartLayer, it.full, slot.cx) }
                // 半心：有下层时先在 heartLayer 垫下层满心填充空缺侧，再把当前层半心叠到更靠前一步。
                HeartLayout.Top.HALF -> {
                    if (slot.baseTier != null) {
                        add(heartLayer, slot.baseTier.full, slot.cx)
                        add(halfRevealTop, slot.topTier.half, slot.cx)
                    } else {
                        add(heartLayer, slot.topTier.half, slot.cx)
                    }
                }
            }
        }

        // 把“世界深度偏移”换算为 billboard 局部 z：billboard 会以 config.scale 缩放局部坐标，故局部 z = 世界偏移 / scale。
        // 世界偏移 = HEART_DEPTH_BIAS × 距离，从而屏幕剪切恒定（亚像素）、深度差随距离自适应。
        val distance = base.length().toFloat()
        val scale = config.scale.coerceAtLeast(1.0e-4f)
        val zStep = (HEART_DEPTH_BIAS.toFloat() * distance) / scale

        billboard(poseStack, base, height, config.scale) {
            drawHeartLayer(collector, poseStack, containerLayer, halfSize, config, 0.0f)
            drawHeartLayer(collector, poseStack, heartLayer, halfSize, config, zStep)
            drawHeartLayer(collector, poseStack, halfRevealTop, halfSize, config, zStep * 2.0f)
        }
        return view.multiplier
    }

    private fun drawHeartLayer(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        groups: Map<Identifier, MutableList<Float>>,
        halfSize: Float,
        config: HealthIndicatorConfig,
        z: Float,
    ) {
        for ((texture, centers) in groups) {
            // 半心按掉血方向翻转填充侧：从右往左扣时填充左半边。
            val flipU = config.drainFromRight && HeartGraphics.isHalfTexture(texture)
            drawHeartGroup(collector, poseStack, texture, centers, halfSize, flipU, z)
        }
    }

    private fun drawHeartGroup(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        texture: Identifier,
        centers: List<Float>,
        halfSize: Float,
        flipU: Boolean,
        z: Float,
    ) {
        if (centers.isEmpty()) return
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture)) { pose, consumer ->
            val m = pose.pose()
            for (cx in centers) {
                HeartGraphics.quad(consumer, m, cx - halfSize, -halfSize, cx + halfSize, halfSize, WHITE, flipU, z)
            }
        }
    }

    private inline fun billboard(poseStack: PoseStack, base: Vec3, height: Double, scale: Float, block: () -> Unit) {
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
