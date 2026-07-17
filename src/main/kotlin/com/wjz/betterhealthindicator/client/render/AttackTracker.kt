package com.wjz.betterhealthindicator.client.render

import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.config.HealthIndicatorConfig
//? if >=26.1 {
import com.wjz.betterhealthindicator.platform.BhiPlatformHooks
//?} else {
/*
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
*/
//?}
import net.minecraft.client.Minecraft
//? if <26.1 {
/*
import net.minecraft.world.InteractionResult
*/
//?}
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * 全局「最近受击目标」追踪：记录本地玩家最近一次攻击的生物与攻击时刻（毫秒墙钟）。
 *
 * 头顶血条（LOOKING_AT 策略）与屏幕面板共用此状态：攻击某生物后，在有效期内即便准星已移开，
 * 仍可让该生物的血条 / 面板持续显示。开关与有效期由全局配置 [HealthIndicatorConfig.trackAttacked]、
 * [HealthIndicatorConfig.attackTrackingSeconds] 控制。
 */
object AttackTracker {
    private var lastAttacked: LivingEntity? = null
    private var lastAttackAtMs: Long = 0L

    fun register() {
        //? if >=26.1 {
        BhiPlatformHooks.registerAttack(::onAttack)
        //?} else {
        /*
        AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
            onAttack(player, entity)
            InteractionResult.PASS
        }
        */
        //?}
        BetterHealthIndicatorLogger.info("Attack tracker registered.")
    }

    private fun onAttack(player: Player, entity: Entity) {
        if (player === Minecraft.getInstance().player && entity is LivingEntity) {
            lastAttacked = entity
            lastAttackAtMs = System.currentTimeMillis()
        }
    }

    /**
     * 当前仍在有效期内的「最近受击目标」；追踪关闭、无记录或已超时返回 null（超时顺带清空）。
     * 仅做时间窗口判定，不含距离 / 可见性 / 墙体遮挡等过滤——由调用方按各自场景的通用过滤决定能否渲染。
     */
    fun tracked(config: HealthIndicatorConfig): LivingEntity? {
        if (!config.trackAttacked) return null
        val attacked = lastAttacked ?: return null
        if (!attacked.isAlive) {
            lastAttacked = null
            return null
        }
        val elapsed = System.currentTimeMillis() - lastAttackAtMs
        if (elapsed > (config.attackTrackingSeconds * 1000.0).toLong()) {
            lastAttacked = null
            return null
        }
        return attacked
    }

    /** 给定实体是否正是当前有效期内的「最近受击目标」。 */
    fun isTracked(entity: LivingEntity, config: HealthIndicatorConfig): Boolean = tracked(config) === entity
}
