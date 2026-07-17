package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.platform.NativeImage
import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import com.wjz.betterhealthindicator.client.compat.BhiIdentifier as Identifier
import com.wjz.betterhealthindicator.client.compat.bhiIdentifier
import com.wjz.betterhealthindicator.client.compat.bhiVanillaIdentifier
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import java.util.function.Supplier

/**
 * 1.20.2 之前原版爱心都挤在 `textures/gui/icons.png` 中，世界渲染却需要一张完整纹理。
 * 首次世界渲染时从当前资源包的 icons.png 裁出独立的 9x9 动态纹理，供头顶血条、粒子和碎片共用。
 */
object LegacyHeartTextures {
    private const val ICONS_NATIVE_SIZE = 256
    private const val HEART_SIZE = 9
    private val ICONS = bhiVanillaIdentifier("textures/gui/icons.png")

    private class Region(val x: Int, val y: Int)

    private val REGIONS = linkedMapOf(
        "container" to Region(16, 0),
        "container_blinking" to Region(25, 0),
        "full" to Region(52, 0),
        "half" to Region(61, 0),
        "container_hardcore" to Region(16, 45),
        "container_hardcore_blinking" to Region(25, 45),
        "hardcore_full" to Region(52, 45),
        "hardcore_half" to Region(61, 45),
    )
    private val IDS = REGIONS.keys.associateWith { name ->
        bhiIdentifier("better_health_indicator", "legacy_heart/$name")
    }

    private var loaded = false
    private var failureReported = false

    /** 仅返回稳定的纹理 ID；此处绝不提前访问尚未就绪的资源管理器。 */
    fun identifier(name: String): Identifier = IDS.getValue(name)

    /** 在渲染线程上懒加载；若资源系统尚未就绪则后续帧继续重试，但只记录一次错误。 */
    fun ensureLoaded() {
        if (loaded) return
        val source = readIcons() ?: return
        try {
            for ((name, region) in REGIONS) {
                register(name, crop(source, region))
            }
            loaded = true
            failureReported = false
        } catch (e: Exception) {
            reportFailure(e)
        } finally {
            source.close()
        }
    }

    private fun readIcons(): NativeImage? = try {
        //? if >=1.19 {
        Minecraft.getInstance().resourceManager.getResource(ICONS).orElse(null)?.open()?.use { NativeImage.read(it) }
        //?} else {
        /*Minecraft.getInstance().resourceManager.getResource(ICONS).inputStream.use { NativeImage.read(it) }*/
        //?}
    } catch (e: Exception) {
        reportFailure(e)
        null
    }

    private fun crop(source: NativeImage, region: Region): NativeImage {
        // GUI UV 以原版 256x256 为逻辑尺寸；高清资源包通常按 2x/4x 等比放大整张 icons.png。
        // 同比裁出 18x18/36x36 等纹理，再由世界四边形缩回 9 个逻辑像素，可保留高清细节。
        val scaleX = (source.width / ICONS_NATIVE_SIZE).coerceAtLeast(1)
        val scaleY = (source.height / ICONS_NATIVE_SIZE).coerceAtLeast(1)
        val width = HEART_SIZE * scaleX
        val height = HEART_SIZE * scaleY
        val sourceX = region.x * scaleX
        val sourceY = region.y * scaleY
        val image = NativeImage(NativeImage.Format.RGBA, width, height, false)
        for (y in 0 until height) {
            for (x in 0 until width) {
                //? if >=1.21.2 {
                image.setPixel(x, y, source.getPixel(sourceX + x, sourceY + y))
                //?} else {
                /*image.setPixelRGBA(x, y, source.getPixelRGBA(sourceX + x, sourceY + y))*/
                //?}
            }
        }
        return image
    }

    private fun register(name: String, image: NativeImage) {
        val id = IDS.getValue(name)
        // DynamicTexture 接管 NativeImage 的生命周期。
        //? if >=1.21.5 {
        Minecraft.getInstance().textureManager.register(id, DynamicTexture(Supplier { "bhi/legacy_heart/$name" }, image))
        //?} else {
        /*Minecraft.getInstance().textureManager.register(id, DynamicTexture(image))*/
        //?}
    }

    private fun reportFailure(error: Exception) {
        if (failureReported) return
        failureReported = true
        BetterHealthIndicatorLogger.error("Failed to prepare legacy vanilla heart textures from $ICONS", error)
    }
}
