package dev.wjz.betterhealthindicator.client.render

import dev.wjz.betterhealthindicator.config.DisplayMode
import dev.wjz.betterhealthindicator.config.HealthIndicatorConfig
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos

/**
 * 实体筛选共享逻辑：距离、可见性、墙体遮挡、显示策略。世界血条与屏幕面板共用，确保一致行为。
 */
object EntitySelector {
    private const val MAX_OCCLUSION_STEPS = 8
    private const val STEP_EPSILON = 0.01
    private const val MAX_ON_SCREEN_FOV_DEGREES = 80.0

    /** 每帧渲染所需的共享上下文。 */
    class Frame(
        val minecraft: Minecraft,
        val level: ClientLevel,
        val camera: Camera,
        val cameraPosition: Vec3,
        val lookedAtEntity: LivingEntity?,
        val tickProgress: Float,
        val config: HealthIndicatorConfig,
    )

    /** 构建当帧上下文；世界或玩家不可用时返回 null。 */
    fun buildFrame(minecraft: Minecraft, config: HealthIndicatorConfig, tickProgress: Float): Frame? {
        val level = minecraft.level ?: return null
        val camera = minecraft.gameRenderer.mainCamera
        val lookedAt = if (config.displayMode == DisplayMode.LOOKING_AT) {
            getLookedAtEntity(minecraft, camera, config.maxDistance)
        } else {
            null
        }
        return Frame(minecraft, level, camera, camera.position(), lookedAt, tickProgress, config)
    }

    fun shouldShow(entity: LivingEntity, frame: Frame): Boolean {
        val config = frame.config
        if (!config.enabled) return false
        if (!config.showSelf && entity === frame.minecraft.player) return false
        if (!entity.isAlive || entity.isInvisible) return false

        val maxHealth = entity.maxHealth
        if (maxHealth <= 0.0f) return false
        if (!config.showFullHealthEntities && entity.health >= maxHealth) return false

        val cameraPosition = frame.cameraPosition
        if (entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z) > config.maxDistanceSquared) {
            return false
        }

        when (config.displayMode) {
            DisplayMode.ALWAYS -> {}
            DisplayMode.LOOKING_AT -> if (entity !== frame.lookedAtEntity) return false
            DisplayMode.ON_SCREEN -> if (!isOnScreen(frame.minecraft, frame.camera, entity.getPosition(frame.tickProgress))) return false
        }

        if (config.occludeBehindWalls) {
            val viewer = frame.minecraft.cameraEntity
            if (viewer != null &&
                isBlockedBySolidWall(frame.level, cameraPosition, entity.getEyePosition(frame.tickProgress), viewer)
            ) {
                return false
            }
        }

        return true
    }

    /** 为屏幕面板挑选当前关注目标：LOOKING_AT 取准星实体；否则取范围内最近的可显示生物。 */
    fun pickPanelTarget(frame: Frame): LivingEntity? {
        if (frame.config.displayMode == DisplayMode.LOOKING_AT) {
            val target = frame.lookedAtEntity
            return if (target != null && shouldShow(target, frame)) target else null
        }

        var best: LivingEntity? = null
        var bestDistSq = Double.MAX_VALUE
        val cameraPosition = frame.cameraPosition
        for (entity in frame.level.entitiesForRendering()) {
            if (entity !is LivingEntity || !shouldShow(entity, frame)) continue
            val distSq = entity.distanceToSqr(cameraPosition.x, cameraPosition.y, cameraPosition.z)
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                best = entity
            }
        }
        return best
    }

    private fun getLookedAtEntity(minecraft: Minecraft, camera: Camera, maxDistance: Double): LivingEntity? {
        val viewer = minecraft.cameraEntity ?: return null
        val eye = camera.position()
        val forward = camera.forwardVector()
        val reach = Vec3(forward.x().toDouble(), forward.y().toDouble(), forward.z().toDouble()).scale(maxDistance)
        val end = eye.add(reach)
        val searchBox = viewer.boundingBox.expandTowards(reach).inflate(1.0)
        val hit = ProjectileUtil.getEntityHitResult(
            viewer,
            eye,
            end,
            searchBox,
            { it is LivingEntity && it !== viewer && it.isAlive },
            maxDistance * maxDistance,
        ) ?: return null
        return hit.entity as? LivingEntity
    }

    private fun isOnScreen(minecraft: Minecraft, camera: Camera, entityPosition: Vec3): Boolean {
        val cameraPosition = camera.position()
        val toEntity = entityPosition.subtract(cameraPosition)
        val length = toEntity.length()
        if (length < 1.0e-4) return true

        val forward = camera.forwardVector()
        val dot = (forward.x() * toEntity.x + forward.y() * toEntity.y + forward.z() * toEntity.z) / length
        val fovDegrees = minecraft.options.fov().get().toDouble().coerceAtMost(MAX_ON_SCREEN_FOV_DEGREES)
        val threshold = cos(Math.toRadians(fovDegrees))
        return dot >= threshold
    }

    /**
     * 沿摄像机到目标点逐段射线检测：命中实心遮挡方块即视为被挡；玻璃等半透明方块穿过继续；流体忽略。
     */
    private fun isBlockedBySolidWall(level: ClientLevel, from: Vec3, to: Vec3, viewer: Entity): Boolean {
        var start = from
        val totalDistanceSq = to.distanceToSqr(from)
        var steps = 0
        while (steps++ < MAX_OCCLUSION_STEPS) {
            val hit = level.clip(
                ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer),
            )

            if (hit.type == HitResult.Type.MISS) {
                return false
            }

            if (level.getBlockState(hit.blockPos).isSolidRender) {
                return true
            }

            val direction = to.subtract(start).normalize()
            start = hit.location.add(direction.scale(STEP_EPSILON))

            if (start.distanceToSqr(from) >= totalDistanceSq) {
                return false
            }
        }

        return false
    }
}
