package com.wjz.betterhealthindicator.client.render

import com.wjz.betterhealthindicator.client.compat.BhiIdentifier as Identifier
import com.mojang.blaze3d.platform.NativeImage
import com.wjz.betterhealthindicator.BetterHealthIndicatorLogger
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import java.util.function.Supplier

/**
 * 多重血条「上层」爱心的运行时染色贴图工厂。
 *
 * 以灰度模板（`template_full` / `template_half`，仅 3 个精确灰度 + 透明）为底，
 * 按给定主色把三档灰度映射成 主体 / 边缘(略暗) / 高光(略亮) 三色，烘焙成 [DynamicTexture] 注册到
 * [net.minecraft.client.renderer.texture.TextureManager]，按主色缓存复用。
 *
 * 相比 `setShaderColor` / 顶点色乘法：乘法只能整体压暗、且三档锁死同一色相（高光做不出提亮）；
 * 这里走「精确调色板替换」，三档颜色各自独立（高光可比主体更亮），像素锐利、每帧零额外开销。
 */
object TintedHeartTextures {
    private val FULL_TEMPLATE = Identifier.fromNamespaceAndPath("better_health_indicator", "textures/heart/template_full.png")
    private val HALF_TEMPLATE = Identifier.fromNamespaceAndPath("better_health_indicator", "textures/heart/template_half.png")

    // 模板里的三档精确灰度（不含 alpha）。
    private const val MAIN_GRAY = 0xFFFFFF // 主体
    private const val EDGE_GRAY = 0xBABABA // 边缘暗光
    private const val HILITE_GRAY = 0x7A7A7A // 高光

    // 由主色派生边缘/高光：边缘整体压暗，高光朝白提亮。调这两个系数即可统一改观感。
    private const val EDGE_FACTOR = 0.80f // 边缘 = 主色 × 此系数（越小越暗）
    private const val HILITE_LERP = 0.6f // 高光 = 主色朝白插值此比例（越大越亮）

    private var loaded = false
    private var templateFull: NativeImage? = null
    private var templateHalf: NativeImage? = null

    // 主色(0xRRGGBB) -> (满心贴图 id, 半心贴图 id)。
    private val cache = HashMap<Int, Pair<Identifier, Identifier>>()

    /** 客户端初始化时预载模板（失败仅记录日志，渲染端会回退到原版红心）。 */
    fun init() {
        ensureTemplates()
        BetterHealthIndicatorLogger.info("Tinted heart templates loaded: ${templateFull != null && templateHalf != null}")
    }

    /** 配置（颜色）变更后调用：释放已注册的染色贴图、清空缓存并丢弃模板，下次按新色重新烘焙。 */
    fun reset() {
        val textureManager = Minecraft.getInstance().textureManager
        for ((full, half) in cache.values) {
            textureManager.release(full)
            textureManager.release(half)
        }
        cache.clear()
        templateFull?.close()
        templateHalf?.close()
        templateFull = null
        templateHalf = null
        loaded = false
    }

    /** 指定主色的满心贴图 id（模板缺失时回退原版红心满心）。 */
    fun fullTexture(colorRgb: Int): Identifier = ensure(colorRgb)?.first ?: HeartGraphics.FULL

    /** 指定主色的半心贴图 id（模板缺失时回退原版红心半心）。 */
    fun halfTexture(colorRgb: Int): Identifier = ensure(colorRgb)?.second ?: HeartGraphics.HALF

    private fun ensure(colorRgb: Int): Pair<Identifier, Identifier>? {
        ensureTemplates()
        val full = templateFull ?: return null
        val half = templateHalf ?: return null
        val key = colorRgb and 0xFFFFFF
        return cache.getOrPut(key) {
            val hex = "%06x".format(key)
            bake(full, key, "full_$hex") to bake(half, key, "half_$hex")
        }
    }

    private fun ensureTemplates() {
        if (loaded) return
        // 仅当两张模板都成功加载才置位 loaded；否则保持未加载以便后续帧重试（避免资源未就绪时永久回退）。
        val full = readImage(FULL_TEMPLATE)
        val half = readImage(HALF_TEMPLATE)
        if (full != null && half != null) {
            templateFull = full
            templateHalf = half
            loaded = true
        } else {
            full?.close()
            half?.close()
        }
    }

    private fun readImage(id: Identifier): NativeImage? = try {
        Minecraft.getInstance().resourceManager.getResource(id).orElse(null)?.open()?.use { NativeImage.read(it) }
    } catch (e: Exception) {
        BetterHealthIndicatorLogger.error("Failed to load heart template $id", e)
        null
    }

    /** 按主色把模板三档灰度映射为 主体/边缘/高光 三色，烘焙成 DynamicTexture 并注册返回其 id。 */
    private fun bake(template: NativeImage, mainRgb: Int, name: String): Identifier {
        val main = mainRgb and 0xFFFFFF
        val edge = darken(main, EDGE_FACTOR)
        val hilite = lighten(main, HILITE_LERP)
        val w = template.width
        val h = template.height
        // DynamicTexture 接管该 NativeImage 的生命周期（release 时自动关闭），故此处不手动 close。
        val image = NativeImage(NativeImage.Format.RGBA, w, h, false)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = template.getPixel(x, y) // ARGB
                val a = (p ushr 24) and 0xFF
                if (a == 0) {
                    image.setPixel(x, y, 0)
                    continue
                }
                val rgb = p and 0xFFFFFF
                val newRgb = when (rgb) {
                    MAIN_GRAY -> main
                    EDGE_GRAY -> edge
                    HILITE_GRAY -> hilite
                    else -> rgb
                }
                image.setPixel(x, y, (a shl 24) or newRgb)
            }
        }
        val id = Identifier.fromNamespaceAndPath("better_health_indicator", "tinted_heart/$name")
        Minecraft.getInstance().textureManager.register(id, DynamicTexture(Supplier { "bhi/$name" }, image))
        return id
    }

    private fun darken(rgb: Int, factor: Float): Int {
        val r = (((rgb ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((rgb ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun lighten(rgb: Int, t: Float): Int {
        val r = lerpTo255((rgb ushr 16) and 0xFF, t)
        val g = lerpTo255((rgb ushr 8) and 0xFF, t)
        val b = lerpTo255(rgb and 0xFF, t)
        return (r shl 16) or (g shl 8) or b
    }

    private fun lerpTo255(c: Int, t: Float): Int = (c + (255 - c) * t).toInt().coerceIn(0, 255)
}
