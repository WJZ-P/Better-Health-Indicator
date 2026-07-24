package com.wjz.betterhealthindicator.platform

//? if forge && >=1.21.6 && <1.21.9 {
/*
import com.mojang.blaze3d.vertex.PoseStack
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent
import net.minecraftforge.client.gui.overlay.ForgeLayer
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Forge 1.21.6–1.21.8 的过渡期桥接。
 *
 * 这些版本已经采用 EventBus 7，但世界渲染仍使用 MultiBufferSource；
 * 因此事件从各类型的静态 BUS 注册，世界绘制由 LevelRendererMixin 派发。
 */
object BhiPlatformHooks {
    private var levelRenderer: ((BhiWorldRenderContext) -> Unit)? = null

    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    // 网易发行包不依赖 Cloth Config，配置仍可直接编辑 JSON。
    fun registerConfigScreen() = Unit

    fun registerAttack(listener: (Player, Entity) -> Unit) {
        AttackEntityEvent.BUS.addListener(Consumer<AttackEntityEvent> { event ->
            listener(event.getEntity(), event.getTarget())
        })
    }

    fun registerLevelRender(renderer: (BhiWorldRenderContext) -> Unit) {
        levelRenderer = renderer
    }

    @JvmStatic
    fun dispatchLevelRender(
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        camera: Camera,
    ) {
        levelRenderer?.invoke(BhiWorldRenderContext(poseStack, buffers, camera, null))
    }

    fun registerEndClientTick(listener: (Minecraft) -> Unit) {
        TickEvent.ClientTickEvent.Post.BUS.addListener(Consumer<TickEvent.ClientTickEvent.Post> {
            listener(Minecraft.getInstance())
        })
    }

    fun registerHud(
        id: BhiIdentifier,
        renderer: (BhiGuiGraphics, Float) -> Unit,
    ) {
        val modBusGroup = FMLJavaModLoadingContext.get().modBusGroup
        AddGuiOverlayLayersEvent.getBus(modBusGroup).addListener(Consumer<AddGuiOverlayLayersEvent> { event ->
            event.getLayeredDraw().addAbove(
                ForgeLayeredDraw.PRE_SLEEP_STACK,
                id,
                ForgeLayeredDraw.BOSS_OVERLAY,
                ForgeLayer { graphics, delta ->
                    renderer(graphics, delta.getGameTimeDeltaPartialTick(false))
                },
            )
        })
    }
}
*/
//?}
