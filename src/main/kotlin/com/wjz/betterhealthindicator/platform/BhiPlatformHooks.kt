package com.wjz.betterhealthindicator.platform

//? if !neoforge && >=26.1 {
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.nio.file.Path

/** 26.1+ Fabric 专属事件与路径桥接；业务代码只依赖这里暴露的加载器无关回调。 */
object BhiPlatformHooks {
    fun configDirectory(): Path = FabricLoader.getInstance().configDir

    fun registerAttack(listener: (Player, Entity) -> Unit) {
        AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
            listener(player, entity)
            InteractionResult.PASS
        }
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        LevelRenderEvents.COLLECT_SUBMITS.register(
            LevelRenderEvents.CollectSubmits { context ->
                renderer(
                    BhiWorldRenderContext(
                        context.poseStack(),
                        context.submitNodeCollector(),
                        context.levelState(),
                    ),
                )
            },
        )
    }

    fun registerEndClientTick(listener: (Minecraft) -> Unit) {
        ClientTickEvents.END_CLIENT_TICK.register(
            ClientTickEvents.EndTick(listener),
        )
    }

    fun registerHud(
        id: BhiIdentifier,
        renderer: (BhiGuiGraphics, Float) -> Unit,
    ) {
        HudElementRegistry.attachElementAfter(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.BOSS_BAR,
            id,
            HudElement { graphics, delta ->
                renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
            },
        )
    }
}
//?}
