package com.wjz.betterhealthindicator.platform

//? if forge && >=1.20 && <1.20.5 {
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

/** Forge 1.20–1.20.4 的传统 EVENT_BUS 世界渲染与 HUD 桥接。 */
object BhiPlatformHooks {
    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    // 网易发行包不依赖 Cloth Config，配置仍可直接编辑 JSON。
    fun registerConfigScreen() = Unit

    fun registerAttack(listener: (net.minecraft.world.entity.player.Player, net.minecraft.world.entity.Entity) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: AttackEntityEvent ->
            listener(event.entity, event.target)
        }
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: RenderLevelStageEvent ->
            if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return@addListener

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
            renderer(event.guiGraphics, event.partialTick)
        }
    }
}
*/
//?}
