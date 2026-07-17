package com.wjz.betterhealthindicator.client.render

import com.wjz.betterhealthindicator.client.compat.MinecraftCompat
import com.wjz.betterhealthindicator.client.compat.BhiMobEffectRef
//? if >=1.21 {
import net.minecraft.core.Holder
import net.minecraft.core.particles.ColorParticleOption
//?}
import net.minecraft.core.particles.ParticleOptions
//? if >=1.21 {
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
//?}
import net.minecraft.world.entity.LivingEntity

/**
 * 「效果粒子 → 状态效果」反查索引。
 *
 * 原版只把玩家自身的效果同步给客户端，其它生物的效果不下发；但每个**可见**效果产生的粒子
 * （[MobEffectInstance.getParticleOptions]）会经同步实体数据 `DATA_EFFECT_PARTICLES` 下发到所有客户端。
 * 因此可在客户端读取这些粒子并反查回效果，作为「内置服务器精确读取」之外、对联机也生效的兜底来源。
 *
 * 映射表在 mod 加载时**动态**遍历 [BuiltInRegistries.MOB_EFFECT] 构建（不写死颜色，Mojang 改色也自动跟随）：
 * - 走默认彩色粒子（[ColorParticleOption]，类型 ENTITY_EFFECT）的效果：按 RGB 建表；**排除瞬间效果**
 *   （瞬间生效不会留下持续粒子，且会与 saturation 撞色）。
 * - 走自定义粒子类型（如不祥之兆/袭击之兆）的效果：按粒子类型建表，天然唯一、更精确。
 */
object MobEffectParticleIndex {
    //? if >=1.21 {
    private val colorToEffect = HashMap<Int, Holder<MobEffect>>()
    private val typeToEffect = HashMap<ParticleType<*>, Holder<MobEffect>>()

    @Volatile
    private var built = false

    // 同步实体数据访问器（私有静态字段，反射取一次缓存）：效果粒子列表 + 是否全为环境(ambient)效果。
    private val particlesAccessor: EntityDataAccessor<List<ParticleOptions>>? = reflectAccessor("DATA_EFFECT_PARTICLES")
    private val ambienceAccessor: EntityDataAccessor<Boolean>? = reflectAccessor("DATA_EFFECT_AMBIENCE_ID")
    //?}

    /** mod 加载时调用以预热映射表（幂等；失败则留待首次使用时重试）。 */
    fun init() {
        //? if >=1.21 {
        build()
        //?}
    }

    private fun build() {
        //? if >=1.21 {
        if (built) return
        synchronized(this) {
            if (built) return
            //? if >=1.21.2 {
            val effects = BuiltInRegistries.MOB_EFFECT.listElements().toList()
            //?} else {
            /*val effects = BuiltInRegistries.MOB_EFFECT.holders().toList()*/
            //?}
            for (holder in effects) {
                val effect = holder.value()
                // 用一个 dummy 实例取「规范粒子」，与运行时同一来源，避免对颜色/工厂细节做任何写死假设。
                val particle = try {
                    MobEffectInstance(holder).particleOptions
                } catch (_: Throwable) {
                    continue
                }
                if (particle is ColorParticleOption) {
                    if (MinecraftCompat.isInstantaneous(effect)) continue
                    colorToEffect.putIfAbsent(rgbOf(particle), holder)
                } else {
                    typeToEffect.putIfAbsent(particle.type, holder)
                }
            }
            built = true
        }
        //?}
    }

    /** 把一个效果粒子反查为对应的状态效果；无法识别（撞色被排除 / 未登记）时返回 null。 */
    fun resolve(particle: ParticleOptions): BhiMobEffectRef? {
        //? if >=1.21 {
        build()
        return if (particle is ColorParticleOption) colorToEffect[rgbOf(particle)] else typeToEffect[particle.type]
        //?} else {
        /*return null*/
        //?}
    }

    /** 读取实体经同步下发的效果粒子列表（客户端对任意被追踪实体均可用）；不可用时返回空。 */
    fun syncedParticles(entity: LivingEntity): List<ParticleOptions> {
        //? if >=1.21 {
        val accessor = particlesAccessor ?: return emptyList()
        return try {
            entity.entityData.get(accessor)
        } catch (_: Throwable) {
            emptyList()
        }
        //?} else {
        /*return emptyList()*/
        //?}
    }

    /** 该实体的同步效果是否「全部为环境(ambient)效果」（用于选择普通/ambient 背板）。 */
    fun allAmbient(entity: LivingEntity): Boolean {
        //? if >=1.21 {
        val accessor = ambienceAccessor ?: return false
        return try {
            entity.entityData.get(accessor)
        } catch (_: Throwable) {
            false
        }
        //?} else {
        /*return false*/
        //?}
    }

    //? if >=1.21 {
    private fun rgbOf(particle: ColorParticleOption): Int {
        val r = (particle.red * 255.0f).toInt().coerceIn(0, 255)
        val g = (particle.green * 255.0f).toInt().coerceIn(0, 255)
        val b = (particle.blue * 255.0f).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun <T : Any> reflectAccessor(name: String): EntityDataAccessor<T>? = try {
        val field = LivingEntity::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        field.get(null) as EntityDataAccessor<T>
    } catch (_: Throwable) {
        null
    }
    //?}
}
