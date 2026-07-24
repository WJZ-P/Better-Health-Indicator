package com.wjz.betterhealthindicator;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.wjz.betterhealthindicator.legacy.LegacyHeartAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/** Full-heart compatibility backend for NetEase's Minecraft 1.16 Forge runtime. */
@Mod(BetterHealthIndicatorNetease.MOD_ID)
public final class BetterHealthIndicatorNetease {
    public static final String MOD_ID = "better_health_indicator";

    private static final Logger LOGGER = LogManager.getLogger("Better Health Indicator");
    private static final double MAX_DISTANCE_SQUARED = 48.0D * 48.0D;
    private static final long TRACK_DURATION_MILLIS = 5_000L;
    private static final float WORLD_SCALE = 0.025F;

    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation TEMPLATE_FULL =
            new ResourceLocation(MOD_ID, "textures/heart/template_full.png");
    private static final ResourceLocation TEMPLATE_HALF =
            new ResourceLocation(MOD_ID, "textures/heart/template_half.png");

    private LivingEntity attackedTarget;
    private long attackedTargetUntil;

    public BetterHealthIndicatorNetease() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Better Health Indicator NetEase 1.16 full-heart backend initialized.");
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getPlayer() == minecraft.player && event.getTarget() instanceof LivingEntity) {
            attackedTarget = (LivingEntity) event.getTarget();
            attackedTargetUntil = System.currentTimeMillis() + TRACK_DURATION_MILLIS;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            LegacyHeartAnimation.tick();
        }
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        LivingEntity entity = event.getEntity();
        if (minecraft.player == null || entity == minecraft.player || entity.isInvisible()) return;
        if (entity.distanceToSqr(minecraft.player) > MAX_DISTANCE_SQUARED) return;

        float maximum = Math.max(1.0F, entity.getMaxHealth());
        float health = Math.max(0.0F, entity.getHealth());
        LegacyHeartAnimation.View view = LegacyHeartAnimation.observe(entity.getId(), health, maximum);
        MatrixStack stack = event.getMatrixStack();

        stack.pushPose();
        stack.translate(0.0D, entity.getBbHeight() + 0.45D, 0.0D);
        stack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        stack.scale(-WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        if (health > 0.0F) {
            renderHeartRow(minecraft, stack, entity.getId(), health, maximum, view, true);
            drawWorldText(minecraft, event, stack, entity.getDisplayName().getString(), view);
        }
        renderParticles(minecraft, stack, entity.getId(), minecraft.getFrameTime(), true);

        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        stack.popPose();
    }

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        LivingEntity target = crosshairTarget(minecraft);
        if (target == null && System.currentTimeMillis() <= attackedTargetUntil) target = attackedTarget;
        if (target == null || !target.isAlive() || target.isInvisible()) return;

        float maximum = Math.max(1.0F, target.getMaxHealth());
        float health = Math.max(0.0F, target.getHealth());
        LegacyHeartAnimation.observe(target.getId(), health, maximum);
        renderHudPanel(minecraft, event.getMatrixStack(), target, health, maximum);
    }

    private static void renderHeartRow(
            Minecraft minecraft,
            MatrixStack stack,
            int entityId,
            float health,
            float maximum,
            LegacyHeartAnimation.View view,
            boolean worldMirrored
    ) {
        for (LegacyHeartAnimation.Slot slot : view.slots) {
            LegacyHeartAnimation.Offset offset =
                    LegacyHeartAnimation.offset(entityId, slot.logicalIndex, health, maximum);
            stack.pushPose();
            stack.translate(slot.x + offset.x, offset.y, 0.0D);
            stack.mulPose(Vector3f.ZP.rotation(offset.rotation));

            drawContainer(minecraft, stack, view.blinking, view.hardcoreContainer, worldMirrored, 1.0F);
            if (slot.baseLayer >= 0) {
                drawFilledHeart(minecraft, stack, LegacyHeartAnimation.Fill.FULL, slot.baseLayer, worldMirrored, 1.0F);
            }
            if (slot.top != LegacyHeartAnimation.Fill.NONE) {
                drawFilledHeart(minecraft, stack, slot.top, slot.topLayer, worldMirrored, 1.0F);
            }
            stack.popPose();
        }
    }

    private static void drawWorldText(
            Minecraft minecraft,
            RenderLivingEvent.Post<?, ?> event,
            MatrixStack stack,
            String name,
            LegacyHeartAnimation.View view
    ) {
        FontRenderer font = minecraft.font;
        font.drawInBatch(
                name,
                -font.width(name) / 2.0F,
                -15.0F,
                0xFFFFFFFF,
                false,
                stack.last().pose(),
                event.getBuffers(),
                false,
                0,
                event.getLight()
        );
        if (view.multiplier > 0) {
            String multiplier = "× " + view.multiplier;
            float rightEdge = -((view.slots.length - 1) * 0.5F * LegacyHeartAnimation.HEART_SPACING)
                    - LegacyHeartAnimation.HEART_SIZE * 0.5F;
            font.drawInBatch(
                    multiplier,
                    rightEdge - font.width(multiplier) - 3.0F,
                    -4.0F,
                    0xFF000000 | LegacyHeartAnimation.multiplierColor(view.multiplier),
                    true,
                    stack.last().pose(),
                    event.getBuffers(),
                    false,
                    0,
                    event.getLight()
            );
        }
    }

    private static void renderParticles(
            Minecraft minecraft,
            MatrixStack stack,
            int entityId,
            float partialTick,
            boolean worldMirrored
    ) {
        List<LegacyHeartAnimation.Particle> particles = LegacyHeartAnimation.particlesFor(entityId);
        for (LegacyHeartAnimation.Particle particle : particles) {
            float alpha = particle.alpha(partialTick);
            if (alpha <= 0.0F) continue;
            stack.pushPose();
            stack.translate(particle.renderX(partialTick), particle.renderY(partialTick), 0.02D);
            stack.mulPose(Vector3f.ZP.rotation(particle.renderRotation(partialTick)));
            if (particle.kind == LegacyHeartAnimation.ParticleKind.HEART) {
                drawFilledHeart(minecraft, stack, particle.fill, particle.layer, worldMirrored, alpha);
            } else {
                drawContainerShard(minecraft, stack, particle.shardQuadrant, worldMirrored, alpha);
            }
            stack.popPose();
        }
    }

    private static void drawContainer(
            Minecraft minecraft,
            MatrixStack stack,
            boolean blinking,
            boolean hardcore,
            boolean flip,
            float alpha
    ) {
        minecraft.getTextureManager().bind(ICONS);
        setColor(0xFFFFFF, alpha);
        int u = blinking ? 25 : 16;
        int v = hardcore ? 45 : 0;
        blitSprite(stack, u, v, 9, 9, flip);
    }

    private static void drawFilledHeart(
            Minecraft minecraft,
            MatrixStack stack,
            LegacyHeartAnimation.Fill fill,
            int layer,
            boolean flip,
            float alpha
    ) {
        if (fill == LegacyHeartAnimation.Fill.NONE) return;
        if (layer <= 0) {
            minecraft.getTextureManager().bind(ICONS);
            setColor(0xFFFFFF, alpha);
            blitSprite(stack, fill == LegacyHeartAnimation.Fill.HALF ? 61 : 52, 0, 9, 9, flip);
        } else {
            minecraft.getTextureManager().bind(fill == LegacyHeartAnimation.Fill.HALF ? TEMPLATE_HALF : TEMPLATE_FULL);
            setColor(LegacyHeartAnimation.colorForLayer(layer), alpha);
            blitSprite(stack, 0, 0, 9, 9, flip, 9, 9);
        }
    }

    private static void drawContainerShard(
            Minecraft minecraft,
            MatrixStack stack,
            int quadrant,
            boolean flip,
            float alpha
    ) {
        minecraft.getTextureManager().bind(ICONS);
        setColor(0xFFFFFF, alpha);
        int sourceX = 16 + ((quadrant & 1) == 0 ? 0 : 4);
        int sourceY = quadrant < 2 ? 0 : 4;
        int size = 5;
        blitSprite(stack, sourceX, sourceY, size, size, flip);
    }

    private static void blitSprite(MatrixStack stack, int u, int v, int width, int height, boolean flip) {
        blitSprite(stack, u, v, width, height, flip, 256, 256);
    }

    private static void blitSprite(
            MatrixStack stack,
            int u,
            int v,
            int width,
            int height,
            boolean flip,
            int textureWidth,
            int textureHeight
    ) {
        int x = -width / 2;
        int y = -height / 2;
        if (flip) {
            stack.pushPose();
            stack.translate(1.0D, 0.0D, 0.0D);
            stack.scale(-1.0F, 1.0F, 1.0F);
            AbstractGui.blit(stack, x, y, u, v, width, height, textureWidth, textureHeight);
            stack.popPose();
        } else {
            AbstractGui.blit(stack, x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }

    private static void renderHudPanel(
            Minecraft minecraft,
            MatrixStack stack,
            LivingEntity target,
            float health,
            float maximum
    ) {
        float ratio = Math.min(1.0F, health / maximum);
        int x = 3;
        int y = 3;
        int width = 142;
        int barX0 = x + 6;
        int barX1 = x + width - 6;
        int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
        String label = target.getDisplayName().getString() + "  "
                + (int) Math.ceil(health) + " / " + (int) Math.ceil(maximum);

        AbstractGui.fill(stack, x, y, x + width, y + 32, 0xB0202020);
        AbstractGui.fill(stack, x, y, x + width, y + 1, 0xFFE0E0E0);
        AbstractGui.fill(stack, barX0 - 1, y + 18, barX1 + 1, y + 27, 0xFF000000);
        AbstractGui.fill(stack, barX0, y + 19, barX1, y + 26, 0xFF303030);
        AbstractGui.fill(stack, barX0, y + 19, fillX, y + 26, healthColor(ratio));
        minecraft.font.drawShadow(stack, label, x + 6.0F, y + 6.0F, 0xFFFFFF);
    }

    private static LivingEntity crosshairTarget(Minecraft minecraft) {
        RayTraceResult hit = minecraft.hitResult;
        if (hit instanceof EntityRayTraceResult) {
            net.minecraft.entity.Entity entity = ((EntityRayTraceResult) hit).getEntity();
            if (entity instanceof LivingEntity) return (LivingEntity) entity;
        }
        return null;
    }

    private static int healthColor(float ratio) {
        int rgb = ratio > 0.5F ? 0x55FF55 : ratio > 0.25F ? 0xFFFF55 : 0xFF5555;
        return 0xFF000000 | rgb;
    }

    private static void setColor(int rgb, float alpha) {
        RenderSystem.color4f(
                ((rgb >>> 16) & 0xFF) / 255.0F,
                ((rgb >>> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                alpha
        );
    }
}
