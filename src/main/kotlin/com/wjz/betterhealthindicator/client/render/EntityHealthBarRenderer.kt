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
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 生物头顶血量渲染。在 26.1 的 [LevelRenderEvents.COLLECT_SUBMITS] 阶段提交几何与文本：
 * - 图形（条 / 爱心）通过 [SubmitNodeCollector.submitCustomGeometry] 提交；
 * - 文本（名字 / 数值）通过 [SubmitNodeCollector.submitText] 自绘提交（自行 billboard 并按字号倍率缩放）。
 *
 * 同时检测生物掉血并交由 [HeartParticleManager] 生成掉落爱心粒子。
 */
object EntityHealthBarRenderer {
    private const val WHITE = -1
    private const val LINE_GAP = +0.1f
    private const val MAX_PARTICLE_BURST = 20

    // 原版浮空名牌的基准字号（局部缩放）；自绘文本以此为基准再乘以 config.textScale。
    private const val NAME_TAG_SCALE = 0.025f
    // —— 文本配色（统一在此调整）——
    // 名字与血量之间的分隔符 / 斜杠颜色（中性灰）。
    private const val SEPARATOR_COLOR = 0xAAAAAA

    // 当前血量：按「当前/最大」比例分档着色（高=绿、中=黄、残血=红）。
    private const val HEALTH_HIGH_RATIO = 0.5f
    private const val HEALTH_MID_RATIO = 0.25f
    private const val HEALTH_HIGH_COLOR = 0x55FF55
    private const val HEALTH_MID_COLOR = 0xFFFF55
    private const val HEALTH_LOW_COLOR = 0xFF5555

    // 最大血量：按绝对血量分档着色，模拟「稀有度渐变」灰→绿→蓝→紫→橙。
    // 每项为 (血量上限, 颜色)，小于该上限取此色；超过所有档位则用 MAX_HEALTH_TOP_COLOR。
    private val MAX_HEALTH_TIERS = arrayOf(
        20f to 0xAAAAAA, // 凡品：灰
        50f to 0x55FF55, // 优秀：绿
        100f to 0x55AAFF, // 精良：蓝
        200f to 0xAA55FF, // 史诗：紫
    )
    private const val MAX_HEALTH_TOP_COLOR = 0xFFAA00 // 传说：橙

    // 爱心相对 container 朝相机方向的“深度偏移量”，按世界单位/每格距离取值（模拟 polygon offset）。
    // 固定的局部 z 偏移会让偏离屏幕中心的心产生横向投影位移（越靠边越大 → 整排被剪切成斜的，即“歪”）；
    // 让世界偏移随距离成正比后，屏幕上的横向剪切恒定且亚像素（看不出歪），而深度差随距离增大，远处也不会 z-fighting。
    // 仅用于打破与 container 的共面平局，故取很小的值即可。
    private const val HEART_DEPTH_BIAS = 0.0015

    // —— 死亡破碎序列 ——
    // 生物死亡后不立刻炸开，而是先「高频抖动」预警，再沿扣血方向逐颗炸裂，更具仪式感。
    private const val DEATH_SHAKE_TICKS = 4 // 预警抖动时长（约 0.2s）
    private const val DEATH_EXPLODE_TICKS = 8 // 逐颗炸开铺开的tick 时常
    private const val DEATH_SHAKE_FREQ = 3.0f // 抖动角频率（弧度/tick），越大越“高频”
    private const val DEATH_SHAKE_AMP = 0.16f // 抖动幅度（占爱心贴图尺寸比例）
    private const val DEATH_MAX = 16 // 同时存在的死亡序列上限

    private val lastHealth = HashMap<Int, Float>()
    private val seenEntities = HashSet<Int>()

    // 本 tick 检测到的待生成粒子：仅记录爱心局部信息，世界坐标推迟到渲染帧解析（见 flushPendingSpawns）。
    private class PendingSpawn(
        val entity: LivingEntity,
        val cx: Float,
        val texture: Identifier,
        val flipU: Boolean,
        val style: HeartParticleManager.ParticleStyle,
        // 被打掉半心的逸散方向：+1 屏幕右、-1 屏幕左、0 满心（无方向、四散）。
        val horizontalDir: Int,
    )

    private val pendingSpawns = ArrayList<PendingSpawn>()

    /** 爱心层贴图片：贴图 + 是否水平翻转（半心填充侧）。 */
    private class TexQuad(val texture: Identifier, val flipU: Boolean)

    /** 死亡序列中的单个 container 槽位：先抖动预警，到点炸裂成碎片（彩色心走原有掉落粒子，不在此处）。 */
    private class DeathHeart(
        val cx: Float,
        val containerTexture: Identifier,
        val explodeTick: Int, // 序列内第几 tick 炸裂（含抖动预警偏移）
        val phase: Float, // 抖动相位，逐心错开更有“颤抖”感
        var exploded: Boolean = false,
    )

    /** 一次 container 破碎的完整序列：固定在死亡处的世界中心，逐 tick 推进抖动与逐颗炸裂。 */
    private class DeathSequence(
        val entityId: Int, // 用于在序列存活期间抑制该实体的活体血条，避免静止 container 叠在抖动上
        val worldX: Double,
        val worldY: Double, // 已含血条高度偏移
        val worldZ: Double,
        val scale: Float,
        val hearts: List<DeathHeart>,
    ) {
        var age = 0
        fun done(): Boolean = hearts.all { it.exploded }
    }

    /** 死亡时待起爆的序列：container 槽位在检测帧即构建，世界坐标延迟到渲染帧按血条同基准解析。 */
    private class PendingDeath(val entity: LivingEntity, val hearts: List<DeathHeart>)

    private val pendingDeaths = ArrayList<PendingDeath>()
    private val deathSequences = ArrayList<DeathSequence>()

    /** 爱心层中的一片待绘四边形：槽位中心 X + 是否水平翻转（决定半心填充侧）+ 竖直偏移（抖动用）。 */
    private class HeartQuad(val cx: Float, val flipU: Boolean, val cy: Float = 0.0f)

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
            tickDeathSequences(minecraft)
        }
    }

    /** 推进死亡破碎序列：到点的爱心碎裂为碎片并迸出彩色心粒子，全部炸完即移除序列。 */
    private fun tickDeathSequences(minecraft: Minecraft) {
        if (deathSequences.isEmpty()) return
        val rotation = minecraft.gameRenderer.mainCamera.rotation()
        val iterator = deathSequences.iterator()
        while (iterator.hasNext()) {
            val seq = iterator.next()
            seq.age++
            for (heart in seq.hearts) {
                if (heart.exploded || seq.age < heart.explodeTick) continue
                heart.exploded = true
                // 该颗心的世界中心：与血条同基准（局部 +cx 经 scale(-x) 与相机朝向映射到世界）。
                val offset = Vector3f(-seq.scale * heart.cx, 0.0f, 0.0f)
                rotation.transform(offset)
                val hx = seq.worldX + offset.x
                val hy = seq.worldY + offset.y
                val hz = seq.worldZ + offset.z
                HeartParticleManager.spawnContainerShards(hx, hy, hz, rotation, heart.containerTexture)
            }
            if (seq.done()) iterator.remove()
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
        val cameraPosition = minecraft.gameRenderer.mainCamera.position()

        // 头顶血条：仅当有可显示的目标实体时绘制；正在播放 container 破碎序列的实体跳过，
        // 否则其静止的 container 会叠在抖动 container 上互相干扰。
        val frame = EntitySelector.buildFrame(minecraft, config, tickProgress, cameraState.cullFrustum)
        if (frame != null) {
            for (entity in frame.level.entitiesForRendering()) {
                if (entity !is LivingEntity) continue
                if (isDying(entity.id)) continue
                if (EntitySelector.shouldShow(entity, frame)) {
                    submit(entity, frame, collector, poseStack, cameraState)
                }
            }
        }

        // 死亡破碎序列与掉落粒子独立于目标实体存在（实体已死亡注销），始终自行渲染。
        if (deathSequences.isNotEmpty()) {
            renderDeathSequences(collector, poseStack, cameraPosition, tickProgress)
        }
        if (!HeartParticleManager.isEmpty()) {
            HeartParticleManager.render(collector, poseStack, cameraPosition, cameraState.orientation, tickProgress)
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
        // 仅在“确实掉血”时生成粒子。注意不能因 current<=0 就跳过：致命一击恰恰是掉到 0，
        // 那一刻同样要让残余爱心爆出（previous>current 已能区分掉血与回血/无变化）。
        if (current >= previous - 0.01f) return

        if (entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) > config.maxDistanceSquared) return

        val maxHealth = entity.maxHealth
        if (maxHealth <= 0.0f) return

        val hpPerHeart = HeartLayout.hpPerHeart(maxHealth, config)
        if (hpPerHeart <= 0.0f) return

        // 致命一击：登记 container 破碎序列（先抖动预警，再沿扣血方向逐颗炸裂）。
        // 与掉落爱心粒子相互独立，各自有开关；彩色心走下方逐心粒子，不在序列中重复处理。
        if (config.containerShatterEnabled && current <= 0.0f && previous > 0.0f) {
            registerDeath(entity, previous, maxHealth, config)
        }

        // 掉落爱心粒子总开关：关闭后仅保留（若启用的）container 破碎，不再迸出彩色心。
        if (!config.damageParticlesEnabled) return

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
            // 被打掉的半边在屏幕的左/右：满→半(afterHalves==1)扣的是 drainFromRight 那侧，半→空(afterHalves==0)反之。
            val horizontalDir = if (!isHalf) {
                0
            } else {
                val rightSide = (afterHalves == 1) == config.drainFromRight
                if (rightSide) 1 else -1
            }
            // 仅登记“待生成”，世界坐标推迟到渲染帧按血条爱心的同一基准解析，确保像素级重合、无缝衔接。
            pendingSpawns.add(PendingSpawn(entity, ref.cx, texture, flipU, style, horizontalDir))
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
        if (pendingSpawns.isEmpty() && pendingDeaths.isEmpty()) return
        val rotation = minecraft.gameRenderer.mainCamera.rotation()

        // 死亡序列：在死亡处（与血条同基准的世界中心）固定下来，后续逐 tick 自行抖动 + 逐颗炸裂。
        for (death in pendingDeaths) {
            if (deathSequences.size >= DEATH_MAX) break
            val position = death.entity.getPosition(tickProgress)
            val height = barLocalY(death.entity, config)
            deathSequences.add(
                DeathSequence(death.entity.id, position.x, position.y + height, position.z, config.scale, death.hearts),
            )
        }
        pendingDeaths.clear()
        if (pendingSpawns.isEmpty()) return
        // 屏幕右方在世界中的水平单位向量（billboard 的 scale(-x) 下，局部 +X→屏幕左，故屏幕右= rotation·(+1,0,0)）。
        val screenRight = Vector3f(1.0f, 0.0f, 0.0f)
        rotation.transform(screenRight)
        var srx = screenRight.x().toDouble()
        var srz = screenRight.z().toDouble()
        val srLen = sqrt(srx * srx + srz * srz)
        if (srLen > 1.0e-6) {
            srx /= srLen
            srz /= srLen
        }
        for (pending in pendingSpawns) {
            val entity = pending.entity
            val position = entity.getPosition(tickProgress)
            val height = barLocalY(entity, config)
            val offset = Vector3f(-config.scale * pending.cx, 0.0f, 0.0f)
            rotation.transform(offset)
            // 半心：朝被打掉那侧（屏幕左/右）逸散；满心：无方向偏置、四散。
            val biasX = srx * pending.horizontalDir
            val biasZ = srz * pending.horizontalDir
            HeartParticleManager.spawn(
                position.x + offset.x,
                position.y + height + offset.y,
                position.z + offset.z,
                pending.texture,
                pending.flipU,
                pending.style,
                biasX,
                biasZ,
            )
        }
        pendingSpawns.clear()
    }

    /**
     * 血条（及粒子、破碎序列）所在的局部竖直偏移（方块）。
     * 取「默认姿态模型真实高度（含马头/耳朵等凸出网格）」与碰撞箱高度的较大者，再加 [HealthIndicatorConfig.yOffset]，
     * 使血条稳定落在生物最高点之上——对头部远高于碰撞箱的生物（如马）不再压头。
     */
    private fun barLocalY(entity: LivingEntity, config: HealthIndicatorConfig): Double {
        val modelTop = EntityModelExtents.get(entity)?.height?.toDouble() ?: 0.0
        return max(modelTop, entity.bbHeight.toDouble()) + config.yOffset
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
        val barHeight = barLocalY(entity, config)
        val distanceSq = entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z)
        val healthRatio = (entity.health / entity.maxHealth).coerceIn(0.0f, 1.0f)

        var heartMultiplier = 0
        when (config.barStyle) {
            BarStyle.BAR -> submitBar(collector, poseStack, base, barHeight, healthRatio, config)
            BarStyle.HEARTS -> heartMultiplier =
                submitHearts(collector, poseStack, base, barHeight, entity.health, entity.maxHealth, config)
            BarStyle.NUMERIC -> {} // 数值样式仅显示文本
        }

        val nameScale = NAME_TAG_SCALE * config.textScale.toFloat()
        if (heartMultiplier > 0) {
            // 超出调色板层数的高血量生物，在血条上方标注「共多少管血」。
            val seg = listOf(TextSegment(styled(Component.literal("x$heartMultiplier"), config), nameScale))
            submitTextLine(collector, poseStack, base, barHeight + LINE_GAP * 5, seg, config, cameraState)
        }

        // 名字 + 血量数值同一行：名字保留自定义命名颜色；血量单独着色、单独字号，并用彩色分隔符隔开。
        val showHp = config.showHealthText || config.barStyle == BarStyle.NUMERIC
        val segments = ArrayList<TextSegment>(3)
        if (config.showName) {
            val nameComp: Component =
                if (config.textBold) entity.displayName.copy().withStyle(ChatFormatting.BOLD) else entity.displayName
            segments.add(TextSegment(nameComp, nameScale))
        }
        if (showHp) {
            if (config.showName) {
                segments.add(TextSegment(styled(Component.literal(" | ").withColor(SEPARATOR_COLOR), config), nameScale))
            }
            val hpScale = NAME_TAG_SCALE * config.healthTextScale.toFloat()
            segments.add(TextSegment(healthComponent(entity, healthRatio, config), hpScale))
        }
        if (segments.isNotEmpty()) {
            // 数值样式无血条几何，文本落在血条基准高度；其余样式落在血条上方。
            val labelY = if (config.barStyle == BarStyle.NUMERIC) barHeight else barHeight + LINE_GAP * 2.5
            submitTextLine(collector, poseStack, base, labelY, segments, config, cameraState)
        }
    }

    /** 构造「当前 / 最大」血量文本：当前值按比例着色（绿/黄/红），最大值按绝对血量分档着色。 */
    private fun healthComponent(entity: LivingEntity, ratio: Float, config: HealthIndicatorConfig): Component {
        val current = ceil(entity.health).toInt()
        val max = ceil(entity.maxHealth).toInt()
        val comp = Component.empty()
            .append(Component.literal("$current").withColor(healthRatioColor(ratio)))
            .append(Component.literal(" / ").withColor(SEPARATOR_COLOR))
            .append(Component.literal("$max").withColor(maxHealthColor(entity.maxHealth)))
        return styled(comp, config)
    }

    /** 按需为文本套用加粗样式。 */
    private fun styled(component: MutableComponent, config: HealthIndicatorConfig): MutableComponent =
        if (config.textBold) component.withStyle(ChatFormatting.BOLD) else component

    /** 当前血量颜色：按当前/最大比例分档，高=绿、中=黄、残血=红。 */
    private fun healthRatioColor(ratio: Float): Int = when {
        ratio > HEALTH_HIGH_RATIO -> HEALTH_HIGH_COLOR
        ratio > HEALTH_MID_RATIO -> HEALTH_MID_COLOR
        else -> HEALTH_LOW_COLOR
    }

    /** 最大血量颜色：按绝对血量沿稀有度渐变分档（与当前血量配色区分），体现生物「血厚程度」。 */
    private fun maxHealthColor(maxHealth: Float): Int {
        for ((upperBound, color) in MAX_HEALTH_TIERS) {
            if (maxHealth < upperBound) return color
        }
        return MAX_HEALTH_TOP_COLOR
    }

    /** 一行内的文本片段：各自的 [Component]（含颜色样式）与最终世界缩放系数。 */
    private data class TextSegment(val text: Component, val scale: Float)

    /**
     * 自绘浮空文本行（替代原版 submitNameTag）：支持同一行内多段、各段独立字号与颜色。
     * 各段按世界宽度水平拼接并整体居中，并按行高在垂直方向居中对齐。
     */
    private fun submitTextLine(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        base: Vec3,
        localY: Double,
        segments: List<TextSegment>,
        config: HealthIndicatorConfig,
        cameraState: CameraRenderState,
    ) {
        val font = Minecraft.getInstance().font
        // occludeBehindWalls：true 走 NORMAL（被墙体遮挡），false 走 SEE_THROUGH（始终可见）。
        val displayMode = if (config.occludeBehindWalls) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH
        val totalWorld = segments.sumOf { (font.width(it.text) * it.scale).toDouble() }.toFloat()
        var penWorld = -totalWorld / 2.0f
        for (seg in segments) {
            val segWorldWidth = font.width(seg.text) * seg.scale
            val halfHeight = font.lineHeight * seg.scale / 2.0f
            poseStack.pushPose()
            try {
                poseStack.translate(base.x, base.y + localY, base.z)
                poseStack.mulPose(cameraState.orientation)
                // 在 billboard 空间内沿世界单位推进笔位（左边缘），并上移半个行高做垂直居中。
                poseStack.translate(penWorld.toDouble(), halfHeight.toDouble(), 0.0)
                // 仅翻转 Y；X 必须保持正，否则四边形绕序反转被字体渲染剔除。
                poseStack.scale(seg.scale, -seg.scale, seg.scale)
                collector.submitText(
                    poseStack,
                    0.0f,
                    0.0f,
                    seg.text.visualOrderText,
                    true,
                    displayMode,
                    LightCoordsUtil.FULL_BRIGHT,
                    WHITE,
                    0,
                    0,
                )
            } finally {
                poseStack.popPose()
            }
            penWorld += segWorldWidth
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

        // 只有两层：container 背板 + 每槽位“自己算出的那一张/两张半心”。
        // 多层血条出现半心时，不再用“下层满心 + 上层半心叠绘”（叠绘会与下层冲突），
        // 而是把当前层半心（保留侧）与下层半心（空缺侧）作为左右互补的两片异色半心**并排不叠**绘制，从根本上规避共面冲突。
        val containerLayer = LinkedHashMap<Identifier, MutableList<HeartQuad>>()
        val heartLayer = LinkedHashMap<Identifier, MutableList<HeartQuad>>()
        fun add(map: LinkedHashMap<Identifier, MutableList<HeartQuad>>, texture: Identifier, cx: Float, flipU: Boolean) =
            map.getOrPut(texture) { ArrayList() }.add(HeartQuad(cx, flipU))

        // 当前层半心的填充侧：drainFromRight 时填充屏幕左半（与血条扣血方向一致）。
        val fillFlip = config.drainFromRight
        for (slot in view.slots) {
            add(containerLayer, HeartGraphics.CONTAINER, slot.cx, false)
            for (q in topHeartQuads(slot, fillFlip)) add(heartLayer, q.texture, slot.cx, q.flipU)
        }

        // 把“世界深度偏移”换算为 billboard 局部 z：billboard 会以 config.scale 缩放局部坐标，故局部 z = 世界偏移 / scale。
        // 世界偏移 = HEART_DEPTH_BIAS × 距离，从而屏幕剪切恒定（亚像素）、深度差随距离自适应。
        val distance = base.length().toFloat()
        val scale = config.scale.coerceAtLeast(1.0e-4f)
        val zStep = (HEART_DEPTH_BIAS.toFloat() * distance) / scale

        billboard(poseStack, base, height, config.scale) {
            drawHeartLayer(collector, poseStack, containerLayer, halfSize, 0.0f)
            drawHeartLayer(collector, poseStack, heartLayer, halfSize, zStep)
        }
        return view.multiplier
    }

    private fun drawHeartLayer(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        groups: Map<Identifier, MutableList<HeartQuad>>,
        halfSize: Float,
        z: Float,
    ) {
        for ((texture, quads) in groups) {
            if (quads.isEmpty()) continue
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture)) { pose, consumer ->
                val m = pose.pose()
                for (q in quads) {
                    HeartGraphics.quad(consumer, m, q.cx - halfSize, q.cy - halfSize, q.cx + halfSize, q.cy + halfSize, WHITE, q.flipU, z)
                }
            }
        }
    }

    /** 计算某槽位「彩色爱心层」要绘制的贴图片（与活体血条一致），供血条与死亡序列复用。 */
    private fun topHeartQuads(slot: HeartLayout.Slot, fillFlip: Boolean): List<TexQuad> = when (slot.top) {
        // 满心：直接画当前层满心（满心对称，flipU 无意义）。
        HeartLayout.Top.FULL -> listOf(TexQuad(slot.topTier.full, false))
        // 空缺：有下层则露出下层满心颜色；无下层就只剩 container（黑底）。
        HeartLayout.Top.NONE -> slot.baseTier?.let { listOf(TexQuad(it.full, false)) } ?: emptyList()
        HeartLayout.Top.HALF -> if (slot.baseTier != null) {
            // 多层半心：左右两片互补异色半心并排（同层同深度、像素互不重叠），避免与下层冲突。
            listOf(TexQuad(slot.topTier.half, fillFlip), TexQuad(slot.baseTier.half, !fillFlip))
        } else {
            // 单层半心：保留侧画当前层颜色，另一侧露出黑色 container。
            listOf(TexQuad(slot.topTier.half, fillFlip))
        }
    }

    /** 该实体是否正在播放 container 破碎序列（含待起爆），用于抑制其活体血条。 */
    private fun isDying(entityId: Int): Boolean =
        deathSequences.any { it.entityId == entityId } || pendingDeaths.any { it.entity.id == entityId }

    /**
     * 致命一击时登记一条 container 破碎序列：按死亡前血量快照取整排槽位位置与炸裂时刻。
     * 沿扣血方向逐颗炸裂——最先清空的那侧（最高 logical，即 slots 末尾）最先炸，依次铺开。
     */
    private fun registerDeath(entity: LivingEntity, previous: Float, maxHealth: Float, config: HealthIndicatorConfig) {
        val slots = HeartLayout.compute(previous, maxHealth, config).slots
        val n = slots.size
        if (n == 0) return
        val hearts = ArrayList<DeathHeart>(n)
        for ((i, slot) in slots.withIndex()) {
            // slots 按 logical 升序；最高 logical（末尾）最先清空，故 rank=0（最先炸）对应末尾。
            val rank = n - 1 - i
            val explodeTick = DEATH_SHAKE_TICKS + if (n <= 1) 0 else rank * DEATH_EXPLODE_TICKS / n
            hearts.add(DeathHeart(slot.cx, HeartGraphics.CONTAINER, explodeTick, (Math.random() * Math.PI * 2.0).toFloat()))
        }
        pendingDeaths.add(PendingDeath(entity, hearts))
    }

    /** 渲染所有进行中的 container 破碎序列：未炸裂的 container 在原位高频抖动，已炸裂的让位给碎片。 */
    private fun renderDeathSequences(
        collector: SubmitNodeCollector,
        poseStack: PoseStack,
        cameraPosition: Vec3,
        partialTick: Float,
    ) {
        val halfSize = HeartGraphics.SIZE / 2.0f
        for (seq in deathSequences) {
            val base = Vec3(seq.worldX - cameraPosition.x, seq.worldY - cameraPosition.y, seq.worldZ - cameraPosition.z)
            val ageRender = seq.age + partialTick
            val ampFactor = 0.4f + 0.6f * min(1.0f, ageRender / DEATH_SHAKE_TICKS)
            val amp = DEATH_SHAKE_AMP * HeartGraphics.SIZE * ampFactor

            val containerLayer = LinkedHashMap<Identifier, MutableList<HeartQuad>>()
            for (heart in seq.hearts) {
                if (heart.exploded) continue
                // 逐心错相位的高频抖动：x/y 不同频率，呈现紧张的“颤抖”而非整齐平移。
                val ox = sin(ageRender * DEATH_SHAKE_FREQ + heart.phase) * amp
                val oy = sin(ageRender * DEATH_SHAKE_FREQ * 1.3f + heart.phase * 1.7f) * amp
                containerLayer.getOrPut(heart.containerTexture) { ArrayList() }.add(HeartQuad(heart.cx + ox, false, oy))
            }

            billboard(poseStack, base, 0.0, seq.scale) {
                drawHeartLayer(collector, poseStack, containerLayer, halfSize, 0.0f)
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
