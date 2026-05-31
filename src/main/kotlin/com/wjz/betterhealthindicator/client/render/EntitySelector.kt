package com.wjz.betterhealthindicator.client.render

import com.wjz.betterhealthindicator.config.DisplayMode
import com.wjz.betterhealthindicator.config.HealthIndicatorConfig
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.culling.Frustum
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

    /** 每帧渲染所需的共享上下文。frustum 为原版当帧视锥体，用于 ON_SCREEN 精确剔除；HUD 等无视锥场景可为 null。 */
    class Frame(
        val minecraft: Minecraft,
        val level: ClientLevel,
        val camera: Camera,
        val cameraPosition: Vec3,
        val lookedAtEntity: LivingEntity?,
        val tickProgress: Float,
        val config: HealthIndicatorConfig,
        val frustum: Frustum?,
    )

    /** 构建当帧上下文；世界或玩家不可用时返回 null。frustum 传原版 cameraRenderState.cullFrustum。 */
    fun buildFrame(
        minecraft: Minecraft,
        config: HealthIndicatorConfig,
        tickProgress: Float,
        frustum: Frustum? = null,
    ): Frame? {
        val level = minecraft.level ?: return null
        val camera = minecraft.gameRenderer.mainCamera
        // 头顶血条的 LOOKING_AT 策略与屏幕面板都需要准星目标，故二者任一启用时都做射线拾取。
        val needLookedAt = config.displayMode == DisplayMode.LOOKING_AT || config.panelEnabled
        val lookedAt = if (needLookedAt) getLookedAtEntity(minecraft, camera, config.maxDistance) else null
        return Frame(minecraft, level, camera, camera.position(), lookedAt, tickProgress, config, frustum)
    }

    fun shouldShow(entity: LivingEntity, frame: Frame): Boolean {
        if (!passesCommonFilters(entity, frame)) return false

        when (frame.config.displayMode) {
            DisplayMode.ALWAYS -> {}
            DisplayMode.LOOKING_AT -> if (entity !== frame.lookedAtEntity) return false
            DisplayMode.ON_SCREEN -> if (!isOnScreen(frame, entity)) return false
        }

        return true
    }

    /** 与显示策略无关的通用过滤：总开关、存活/可见、血量、距离、实心墙遮挡。 */
    private fun passesCommonFilters(entity: LivingEntity, frame: Frame): Boolean {
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

    /** 屏幕面板目标：仅当准星正对生物、在范围内、且未被实心墙遮挡时返回，否则不显示。 */
    fun pickPanelTarget(frame: Frame): LivingEntity? {
        val target = frame.lookedAtEntity ?: return null
        return if (passesCommonFilters(target, frame)) target else null
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

    /**
     * 是否在屏幕视野内。优先使用原版当帧视锥体（矩形视锥，精确）做剔除；
     * 无视锥可用时（如 HUD 场景）退化为点积圆锥粗筛。
     */
    private fun isOnScreen(frame: Frame, entity: LivingEntity): Boolean {
        val frustum = frame.frustum
        if (frustum != null) {
            // 视锥体与实体包围盒同为世界坐标；略微外扩，避免模型超出碰撞箱的部分在边缘被误剔除。
            return frustum.isVisible(entity.boundingBox.inflate(0.25))
        }
        return isInViewCone(frame.camera, entity.getPosition(frame.tickProgress))
    }

    /** 点积圆锥粗筛兜底：比较视线方向与“指向实体方向”的夹角是否落在 FOV 内。 */
    private fun isInViewCone(camera: Camera, entityPosition: Vec3): Boolean {
        val cameraPosition = camera.position()
        val toEntity: Vec3 = entityPosition.subtract(cameraPosition)
        val length = toEntity.length() // 实体距离太近会触发除 0，直接判定可见。
        if (length < 1.0e-4) return true

        val forward = camera.forwardVector()
        val dot = (forward.x() * toEntity.x + forward.y() * toEntity.y + forward.z() * toEntity.z) / length
        val fovDegrees = Minecraft.getInstance().options.fov().get().toDouble()
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
