package com.wjz.betterhealthindicator.platform

//? if forge && >=1.21.9 {
/*
import com.mojang.blaze3d.vertex.PoseStack
import com.wjz.betterhealthindicator.client.compat.BhiGuiGraphics
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier
import com.wjz.betterhealthindicator.client.compat.BhiWorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.LevelRenderState
//?} else {
import net.minecraft.client.renderer.state.LevelRenderState
//?}
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent
import net.minecraftforge.client.gui.overlay.ForgeLayer
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.AttackEntityEvent
import net.minecraftforge.fml.loading.FMLPaths
import java.nio.file.Path
import java.util.function.Consumer

/** Forge 1.21.9+ 的 EventBus 7 与提交节点渲染桥接。 */
object BhiPlatformHooks {
    private var levelRenderer: ((BhiWorldRenderContext) -> Unit)? = null

    fun configDirectory(): Path = FMLPaths.CONFIGDIR.get()

    // Cloth Config has no Forge artifact for these Minecraft versions.
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
        collector: SubmitNodeCollector,
        state: LevelRenderState,
    ) {
        levelRenderer?.invoke(BhiWorldRenderContext(poseStack, collector, state))
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
        AddGuiOverlayLayersEvent.BUS.addListener(Consumer<AddGuiOverlayLayersEvent> { event ->
            event.getLayeredDraw().addAbove(
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
