package com.wjz.betterhealthindicator.platform

//? if forge && >=1.19 && <1.20 {
/*
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

/** Forge 1.19 的传统 EVENT_BUS 世界渲染与 PoseStack HUD 桥接。 */
object BhiPlatformHooks {
    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    fun registerConfigScreen() = Unit

    fun registerAttack(listener: (net.minecraft.world.entity.player.Player, net.minecraft.world.entity.Entity) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: AttackEntityEvent ->
            listener(event.entity, event.target)
        }
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: RenderLevelStageEvent ->
            // 1.19.2 尚无 AFTER_ENTITIES；AFTER_PARTICLES 是首个稳定的实体后阶段。
            if (event.stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return@addListener

            val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
            try {
                renderer(BhiWorldRenderContext(event.poseStack, buffers, event.camera, event.frustum))
            } finally {
                buffers.endBatch()
            }
        }
    }

    fun registerEndClientTick(listener: (Minecraft) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: TickEvent.ClientTickEvent ->
            if (event.phase == TickEvent.Phase.END) {
                listener(Minecraft.getInstance())
            }
        }
    }

    fun registerHud(
        @Suppress("UNUSED_PARAMETER") id: BhiIdentifier,
        renderer: (BhiGuiGraphics, Float) -> Unit,
    ) {
        MinecraftForge.EVENT_BUS.addListener { event: RenderGuiEvent.Post ->
            renderer(BhiGuiGraphics(event.poseStack), event.partialTick)
        }
    }
}
*/
//?}
