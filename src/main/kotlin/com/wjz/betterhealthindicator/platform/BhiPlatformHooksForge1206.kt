package com.wjz.betterhealthindicator.platform

//? if forge && >=1.20.5 && <1.21 {
/*
import com.mojang.blaze3d.vertex.PoseStack
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

/** Forge 1.20.5–1.20.6：FrameGraph 世界事件 + 原版分层 HUD 的桥接。 */
object BhiPlatformHooks {
    private var hudRenderer: ((BhiGuiGraphics, Float) -> Unit)? = null

    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    fun registerConfigScreen() = Unit

    fun registerAttack(listener: (net.minecraft.world.entity.player.Player, net.minecraft.world.entity.Entity) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: AttackEntityEvent ->
            listener(event.entity, event.target)
        }
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: RenderLevelStageEvent ->
            if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return@addListener

            val poseStack = PoseStack()
            poseStack.mulPose(event.poseStack)
            val buffers = Minecraft.getInstance().renderBuffers().bufferSource()
            try {
                renderer(BhiWorldRenderContext(poseStack, buffers, event.camera, event.frustum))
            } finally {
                buffers.endBatch()
            }
        }
    }

    fun registerEndClientTick(listener: (Minecraft) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { _: TickEvent.ClientTickEvent.Post ->
            listener(Minecraft.getInstance())
        }
    }

    fun registerHud(
        @Suppress("UNUSED_PARAMETER") id: BhiIdentifier,
        renderer: (BhiGuiGraphics, Float) -> Unit,
    ) {
        hudRenderer = renderer
    }

    @JvmStatic
    fun dispatchHud(graphics: BhiGuiGraphics, tickProgress: Float) {
        hudRenderer?.invoke(graphics, tickProgress)
    }
}
*/
//?}
