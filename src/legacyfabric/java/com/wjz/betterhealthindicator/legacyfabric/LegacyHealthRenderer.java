package com.wjz.betterhealthindicator.legacyfabric;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.LivingEntity;

public final class LegacyHealthRenderer {
    private static final double MAX_DISTANCE_SQUARED = 48.0 * 48.0;

    private LegacyHealthRenderer() {
    }

    public static void renderWorldBar(
            LivingEntity entity,
            double x,
            double y,
            double z,
            float tickDelta
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || entity == client.player || !entity.isAlive() || entity.isInvisible()) return;
        if (x * x + y * y + z * z > MAX_DISTANCE_SQUARED) return;

        float maximum = Math.max(1.0F, entity.getMaxHealth());
        float health = Math.max(0.0F, entity.getHealth());
        float ratio = Math.min(1.0F, health / maximum);
        String name = entity.getEntityName();
        String label = name + "  " + (int) Math.ceil(health) + " / " + (int) Math.ceil(maximum);
        TextRenderer font = client.textRenderer;
        int width = Math.max(40, font.getStringWidth(label) + 4);
        int half = width / 2;
        int fill = Math.min(half, -half + (int) (width * ratio));
        int color = ratio > 0.5F ? 0xE055FF55 : ratio > 0.25F ? 0xE0FFFF55 : 0xE0FF5555;
        EntityRenderDispatcher dispatcher = client.getEntityRenderManager();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height + 0.35, z);
        GlStateManager.rotate(-dispatcher.yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(dispatcher.pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.025F, -0.025F, 0.025F);
        GlStateManager.enableBlend();
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(false);
        DrawableHelper.fill(-half - 1, -2, half + 1, 4, 0xC0000000);
        DrawableHelper.fill(-half, -1, half, 3, 0xB0202020);
        DrawableHelper.fill(-half, -1, fill, 3, color);
        font.drawWithShadow(label, -font.getStringWidth(label) / 2.0F, -13.0F, 0xFFFFFF);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static void renderHud() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.targetedEntity instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) client.targetedEntity;
        if (!target.isAlive() || target.isInvisible()) return;

        float maximum = Math.max(1.0F, target.getMaxHealth());
        float health = Math.max(0.0F, target.getHealth());
        float ratio = Math.min(1.0F, health / maximum);
        int x = 3;
        int y = 3;
        int width = 126;
        int barX0 = x + 6;
        int barX1 = x + width - 6;
        int fillX = barX0 + (int) ((barX1 - barX0) * ratio);
        int color = ratio > 0.5F ? 0xFF55FF55 : ratio > 0.25F ? 0xFFFFFF55 : 0xFFFF5555;
        String label = target.getEntityName() + "  " + (int) Math.ceil(health) + " / " + (int) Math.ceil(maximum);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableDepthTest();
        DrawableHelper.fill(x, y, x + width, y + 32, 0xB0202020);
        DrawableHelper.fill(x, y, x + width, y + 1, 0xFFE0E0E0);
        DrawableHelper.fill(barX0 - 1, y + 18, barX1 + 1, y + 27, 0xFF000000);
        DrawableHelper.fill(barX0, y + 19, barX1, y + 26, 0xFF303030);
        DrawableHelper.fill(barX0, y + 19, fillX, y + 26, color);
        client.textRenderer.drawWithShadow(label, x + 6.0F, y + 6.0F, 0xFFFFFF);
        GlStateManager.enableDepthTest();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
