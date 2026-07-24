package com.wjz.betterhealthindicator;

import com.wjz.betterhealthindicator.legacy.LegacyHeartAnimation;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Full-heart compatibility backend for NetEase's Minecraft 1.7.10 Forge runtime. */
@Mod(
        modid = BetterHealthIndicatorNetease.MOD_ID,
        name = BetterHealthIndicatorNetease.MOD_NAME,
        version = BetterHealthIndicatorNetease.VERSION,
        acceptedMinecraftVersions = "[1.7.10]"
)
public final class BetterHealthIndicatorNetease {
    public static final String MOD_ID = "better_health_indicator";
    public static final String MOD_NAME = "Better Health Indicator";
    public static final String VERSION = "1.0.0+netease1.7.10";

    private static final double MAX_DISTANCE_SQUARED = 48.0D * 48.0D;
    private static final long TRACK_DURATION_MILLIS = 5_000L;
    private static final float WORLD_SCALE = 0.025F;
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation TEMPLATE_FULL =
            new ResourceLocation(MOD_ID, "textures/heart/template_full.png");
    private static final ResourceLocation TEMPLATE_HALF =
            new ResourceLocation(MOD_ID, "textures/heart/template_half.png");

    private EntityLivingBase attackedTarget;
    private long attackedTargetUntil;

    @Mod.EventHandler
    public void onInitialize(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        // ClientTickEvent is posted on the FML bus in 1.7.10, not the Forge event bus.
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.entityPlayer == minecraft.thePlayer && event.target instanceof EntityLivingBase) {
            attackedTarget = (EntityLivingBase) event.target;
            attackedTargetUntil = System.currentTimeMillis() + TRACK_DURATION_MILLIS;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getMinecraft().isGamePaused()) {
            LegacyHeartAnimation.tick();
        }
    }

    @SubscribeEvent
    public void onRenderLiving(RenderLivingEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!(event.entity instanceof EntityLivingBase)) return;
        EntityLivingBase entity = (EntityLivingBase) event.entity;
        if (minecraft.thePlayer == null || entity == minecraft.thePlayer || entity.isInvisible()) return;
        if (distanceSquared(entity, minecraft.thePlayer) > MAX_DISTANCE_SQUARED) return;

        float maximum = Math.max(1.0F, entity.getMaxHealth());
        float health = Math.max(0.0F, entity.getHealth());
        LegacyHeartAnimation.View view = LegacyHeartAnimation.observe(entity.getEntityId(), health, maximum);

        RenderManager renderManager = RenderManager.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(event.x, event.y + entity.height + 0.45D, event.z);
        GL11.glRotatef(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(
                (minecraft.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F) * renderManager.playerViewX,
                1.0F,
                0.0F,
                0.0F
        );
        GL11.glScalef(-WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (health > 0.0F) {
            renderHeartRow(minecraft, entity.getEntityId(), health, maximum, view, true);
            drawWorldText(minecraft, entity, view);
        }
        renderParticles(minecraft, entity.getEntityId(), 1.0F, true);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.gameSettings.hideGUI) return;

        EntityLivingBase target = crosshairTarget(minecraft);
        if (target == null && System.currentTimeMillis() <= attackedTargetUntil) target = attackedTarget;
        if (target == null || !target.isEntityAlive() || target.isInvisible()) return;

        float maximum = Math.max(1.0F, target.getMaxHealth());
        float health = Math.max(0.0F, target.getHealth());
        LegacyHeartAnimation.observe(target.getEntityId(), health, maximum);
        renderHud(minecraft, target, health, maximum);
    }

    private static void renderHeartRow(
            Minecraft minecraft,
            int entityId,
            float health,
            float maximum,
            LegacyHeartAnimation.View view,
            boolean worldMirrored
    ) {
        for (LegacyHeartAnimation.Slot slot : view.slots) {
            LegacyHeartAnimation.Offset offset =
                    LegacyHeartAnimation.offset(entityId, slot.logicalIndex, health, maximum);
            GL11.glPushMatrix();
            GL11.glTranslatef(slot.x + offset.x, offset.y, 0.0F);
            GL11.glRotatef((float) Math.toDegrees(offset.rotation), 0.0F, 0.0F, 1.0F);
            drawContainer(minecraft, view.blinking, view.hardcoreContainer, worldMirrored, 1.0F);
            if (slot.baseLayer >= 0) {
                drawFilledHeart(minecraft, LegacyHeartAnimation.Fill.FULL, slot.baseLayer, worldMirrored, 1.0F);
            }
            if (slot.top != LegacyHeartAnimation.Fill.NONE) {
                drawFilledHeart(minecraft, slot.top, slot.topLayer, worldMirrored, 1.0F);
            }
            GL11.glPopMatrix();
        }
    }

    private static void drawWorldText(
            Minecraft minecraft,
            EntityLivingBase entity,
            LegacyHeartAnimation.View view
    ) {
        FontRenderer font = minecraft.fontRendererObj;
        String name = entity.getCommandSenderName();
        font.drawString(name, -font.getStringWidth(name) / 2, -15, 0xFFFFFFFF);
        if (view.multiplier > 0) {
            String multiplier = "× " + view.multiplier;
            float rightEdge = -((view.slots.length - 1) * 0.5F * LegacyHeartAnimation.HEART_SPACING)
                    - LegacyHeartAnimation.HEART_SIZE * 0.5F;
            font.drawStringWithShadow(
                    multiplier,
                    (int) (rightEdge - font.getStringWidth(multiplier) - 3.0F),
                    -4,
                    0xFF000000 | LegacyHeartAnimation.multiplierColor(view.multiplier)
            );
        }
    }

    private static void renderParticles(
            Minecraft minecraft,
            int entityId,
            float partialTick,
            boolean worldMirrored
    ) {
        List<LegacyHeartAnimation.Particle> particles = LegacyHeartAnimation.particlesFor(entityId);
        for (LegacyHeartAnimation.Particle particle : particles) {
            float alpha = particle.alpha(partialTick);
            if (alpha <= 0.0F) continue;
            GL11.glPushMatrix();
            GL11.glTranslatef(particle.renderX(partialTick), particle.renderY(partialTick), 0.02F);
            GL11.glRotatef(
                    (float) Math.toDegrees(particle.renderRotation(partialTick)),
                    0.0F,
                    0.0F,
                    1.0F
            );
            if (particle.kind == LegacyHeartAnimation.ParticleKind.HEART) {
                drawFilledHeart(minecraft, particle.fill, particle.layer, worldMirrored, alpha);
            } else {
                drawContainerShard(minecraft, particle.shardQuadrant, worldMirrored, alpha);
            }
            GL11.glPopMatrix();
        }
    }

    private static void drawContainer(
            Minecraft minecraft,
            boolean blinking,
            boolean hardcore,
            boolean flip,
            float alpha
    ) {
        minecraft.getTextureManager().bindTexture(ICONS);
        setColor(0xFFFFFF, alpha);
        blitSprite(blinking ? 25 : 16, hardcore ? 45 : 0, 9, 9, flip, 256, 256);
    }

    private static void drawFilledHeart(
            Minecraft minecraft,
            LegacyHeartAnimation.Fill fill,
            int layer,
            boolean flip,
            float alpha
    ) {
        if (fill == LegacyHeartAnimation.Fill.NONE) return;
        if (layer <= 0) {
            minecraft.getTextureManager().bindTexture(ICONS);
            setColor(0xFFFFFF, alpha);
            blitSprite(fill == LegacyHeartAnimation.Fill.HALF ? 61 : 52, 0, 9, 9, flip, 256, 256);
        } else {
            minecraft.getTextureManager().bindTexture(fill == LegacyHeartAnimation.Fill.HALF
                    ? TEMPLATE_HALF : TEMPLATE_FULL);
            setColor(LegacyHeartAnimation.colorForLayer(layer), alpha);
            blitSprite(0, 0, 9, 9, flip, 9, 9);
        }
    }

    private static void drawContainerShard(
            Minecraft minecraft,
            int quadrant,
            boolean flip,
            float alpha
    ) {
        minecraft.getTextureManager().bindTexture(ICONS);
        setColor(0xFFFFFF, alpha);
        int sourceX = 16 + ((quadrant & 1) == 0 ? 0 : 4);
        int sourceY = quadrant < 2 ? 0 : 4;
        blitSprite(sourceX, sourceY, 5, 5, flip, 256, 256);
    }

    private static void blitSprite(
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
            GL11.glPushMatrix();
            GL11.glTranslatef(1.0F, 0.0F, 0.0F);
            GL11.glScalef(-1.0F, 1.0F, 1.0F);
            Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, textureWidth, textureHeight);
            GL11.glPopMatrix();
        } else {
            Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }

    private static void renderHud(Minecraft minecraft, EntityLivingBase target, float health, float maximum) {
        float ratio = Math.min(1.0F, health / maximum);
        int x = 3;
        int y = 3;
        int width = 142;
        int barX0 = x + 6;
        int barX1 = x + width - 6;
        int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
        String label = target.getCommandSenderName() + "  "
                + (int) Math.ceil(health) + " / " + (int) Math.ceil(maximum);

        Gui.drawRect(x, y, x + width, y + 32, 0xB0202020);
        Gui.drawRect(x, y, x + width, y + 1, 0xFFE0E0E0);
        Gui.drawRect(barX0 - 1, y + 18, barX1 + 1, y + 27, 0xFF000000);
        Gui.drawRect(barX0, y + 19, barX1, y + 26, 0xFF303030);
        Gui.drawRect(barX0, y + 19, fillX, y + 26, healthColor(ratio));
        minecraft.fontRendererObj.drawStringWithShadow(label, x + 6, y + 6, 0xFFFFFF);
    }

    private static EntityLivingBase crosshairTarget(Minecraft minecraft) {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit instanceof EntityLivingBase) {
            return (EntityLivingBase) hit.entityHit;
        }
        return null;
    }

    private static double distanceSquared(EntityLivingBase first, EntityLivingBase second) {
        double x = first.posX - second.posX;
        double y = first.posY - second.posY;
        double z = first.posZ - second.posZ;
        return x * x + y * y + z * z;
    }

    private static int healthColor(float ratio) {
        int rgb = ratio > 0.5F ? 0x55FF55 : ratio > 0.25F ? 0xFFFF55 : 0xFF5555;
        return 0xFF000000 | rgb;
    }

    private static void setColor(int rgb, float alpha) {
        GL11.glColor4f(
                ((rgb >>> 16) & 0xFF) / 255.0F,
                ((rgb >>> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                alpha
        );
    }
}
