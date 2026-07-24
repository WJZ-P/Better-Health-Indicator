package com.wjz.betterhealthindicator.platform

//? if forge && >=1.18 && <1.19 {
/*
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.event.RenderLevelLastEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

/** Forge 1.18 的 RenderLevelLastEvent 与旧 HUD 事件桥接。 */
object BhiPlatformHooks {
    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    fun registerConfigScreen() = Unit

    fun registerAttack(listener: (net.minecraft.world.entity.player.Player, net.minecraft.world.entity.Entity) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: AttackEntityEvent ->
            listener(event.player, event.target)
        }
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: RenderLevelLastEvent ->
            val minecraft = Minecraft.getInstance()
            val buffers = minecraft.renderBuffers().bufferSource()
            try {
                renderer(
                    BhiWorldRenderContext(
                        event.poseStack,
                        buffers,
                        minecraft.gameRenderer.mainCamera,
                        null,
                    ),
                )
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
        MinecraftForge.EVENT_BUS.addListener { event: RenderGameOverlayEvent.Post ->
            if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
                renderer(BhiGuiGraphics(event.matrixStack), event.partialTicks)
            }
        }
    }
}
*/
//?}
