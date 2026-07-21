package com.wjz.betterhealthindicator.platform

//? if forge && <1.21.9 {
/*
import com.mojang.blaze3d.vertex.PoseStack
import com.wjz.betterhealthindicator.client.gui.ClothConfigScreenFactory
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.LayeredDraw
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path

/** Forge 1.21.1 的事件与路径桥接。 */
object BhiPlatformHooks {
    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    fun registerConfigScreen() {
        if (ModList.get().isLoaded(CLOTH_CONFIG_MOD_ID)) {
            MinecraftForge.registerConfigScreen { parent -> ClothConfigScreenFactory.create(parent) }
        }
    }

    fun registerAttack(listener: (Player, Entity) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: AttackEntityEvent ->
            listener(event.entity, event.target)
        }
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        MinecraftForge.EVENT_BUS.addListener { event: RenderLevelStageEvent ->
            if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return@addListener

            // Forge 1.21.1 has already installed the camera view matrix in
            // RenderSystem's global model-view stack at this stage. Applying
            // event.poseStack here would rotate every submitted vertex twice.
            val poseStack = PoseStack()
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
        id: BhiIdentifier,
        renderer: (BhiGuiGraphics, Float) -> Unit,
    ) {
        FMLJavaModLoadingContext.get().modEventBus.addListener { event: AddGuiOverlayLayersEvent ->
            event.layeredDraw.addAbove(
                ForgeLayeredDraw.PRE_SLEEP_STACK,
                id,
                ForgeLayeredDraw.BOSS_OVERLAY,
                LayeredDraw.Layer { graphics, delta ->
                    renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
                },
            )
        }
    }

    private const val CLOTH_CONFIG_MOD_ID = "cloth_config"
}
*/
//?}
