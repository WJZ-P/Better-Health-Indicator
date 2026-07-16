package com.wjz.betterhealthindicator.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 默认姿态下实体模型的真实网格范围（方块单位），是「屏幕面板缩放」与「头顶血条 Y 偏移」的共用真相源。
 *
 * 通过原版模型部件的 GUI 范围 API（旧版则遍历 cube）累计所有顶点的 AABB，
 * 比碰撞箱（仅物理体积）精确：如马头/耳朵、牛嘴等凸出网格都会被计入。
 *
 * 按 [EntityType] 缓存：同种实体默认姿态尺寸固定，只需算一次，避免每帧遍历所有 cube 顶点。
 */
object EntityModelExtents {
    /**
     * @param height 模型竖直高度（脚到最高网格，方块）
     * @param horizontalDiagonal 水平足迹(X×Z)对角线，绕行到任意角度的最坏横向投影
     */
    class Extents(val height: Float, val horizontalDiagonal: Float)

    // 值可为 null（非生物渲染器/空模型），用 containsKey 区分“已算过的 null”与“未算过”，防止反复重算。
    private val cache = HashMap<EntityType<*>, Extents?>()

    fun get(entity: LivingEntity): Extents? {
        val type = entity.type
        if (cache.containsKey(type)) return cache[type]
        val computed = compute(entity)
        cache[type] = computed
        return computed
    }

    /**
     * 测量前先把所有部件重置到初始姿态：模型对象为全局共享、每帧被 setupAnim 改写，否则会量到动画中途的姿势
     * （走路时腿张开等），导致尺寸忽大忽小。非生物渲染器或空模型返回 null，调用方退化为碰撞箱兜底。
     */
    private fun compute(entity: LivingEntity): Extents? {
        val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(entity)
        val model = (renderer as? LivingEntityRenderer<*, *, *>)?.model ?: return null
        val root = model.root()
        root.getAllParts().forEach { it.resetPose() }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        //? if >=1.21.9 {
        root.getExtentsForGui(PoseStack()) { v ->
            minX = min(minX, v.x()); maxX = max(maxX, v.x())
            minY = min(minY, v.y()); maxY = max(maxY, v.y())
            minZ = min(minZ, v.z()); maxZ = max(maxZ, v.z())
        }
        //?} else if >=1.21.5 {
        /*val vertices = HashSet<org.joml.Vector3f>()
        root.getExtentsForGui(PoseStack(), vertices)
        for (v in vertices) {
            minX = min(minX, v.x()); maxX = max(maxX, v.x())
            minY = min(minY, v.y()); maxY = max(maxY, v.y())
            minZ = min(minZ, v.z()); maxZ = max(maxZ, v.z())
        }*/
        //?} else {
        /*root.visit(PoseStack()) { pose, _, _, cube ->
            val matrix = pose.pose()
            for (x in floatArrayOf(cube.minX, cube.maxX)) {
                for (y in floatArrayOf(cube.minY, cube.maxY)) {
                    for (z in floatArrayOf(cube.minZ, cube.maxZ)) {
                        val v = matrix.transformPosition(org.joml.Vector3f(x / 16.0f, y / 16.0f, z / 16.0f))
                        minX = min(minX, v.x()); maxX = max(maxX, v.x())
                        minY = min(minY, v.y()); maxY = max(maxY, v.y())
                        minZ = min(minZ, v.z()); maxZ = max(maxZ, v.z())
                    }
                }
            }
        }*/
        //?}
        if (minX > maxX) return null // 模型无任何 cube 时跳过
        val xExtent = maxX - minX
        val yExtent = maxY - minY
        val zExtent = maxZ - minZ
        return Extents(height = yExtent, horizontalDiagonal = sqrt(xExtent * xExtent + zExtent * zExtent))
    }
}
