package com.wjz.betterhealthindicator.platform

//? if neoforge && <26.1 {
/*
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import com.wjz.betterhealthindicator.client.compat.MinecraftCompat
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import java.nio.file.Path

/** NeoForge 1.20–1.21 的直接缓冲事件与路径桥接。 */
object BhiPlatformHooks {
    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    fun registerAttack(listener: (Player, Entity) -> Unit) {
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent::class.java) { event ->
            listener(event.entity, event.target)
        }
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterEntities::class.java) { event ->
            val minecraft = Minecraft.getInstance()
            val buffers = minecraft.renderBuffers().bufferSource()
            try {
                renderer(
                    BhiWorldRenderContext(
                        event.poseStack,
                        buffers,
                        MinecraftCompat.mainCamera(minecraft),
                        null,
                    ),
                )
            } finally {
                buffers.endBatch()
            }
        }
    }

    fun registerEndClientTick(listener: (Minecraft) -> Unit) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post::class.java) {
            listener(Minecraft.getInstance())
        }
    }

    fun registerHud(
        @Suppress("UNUSED_PARAMETER") id: BhiIdentifier,
        renderer: (BhiGuiGraphics, Float) -> Unit,
    ) {
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post::class.java) { event ->
            renderer(event.guiGraphics, MinecraftCompat.tickProgress(Minecraft.getInstance()))
        }
    }
}
*/
//?}
