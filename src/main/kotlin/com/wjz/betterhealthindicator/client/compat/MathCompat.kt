package com.wjz.betterhealthindicator.client.compat

//? if >=1.19.3 {
typealias BhiVector3f = org.joml.Vector3f
typealias BhiQuaternionf = org.joml.Quaternionf
typealias BhiMatrix4f = org.joml.Matrix4f
//?} else {
/*typealias BhiVector3f = com.mojang.math.Vector3f
typealias BhiQuaternionf = com.mojang.math.Quaternion
typealias BhiMatrix4f = com.mojang.math.Matrix4f*/
//?}

fun bhiVector3f(x: Float, y: Float, z: Float): BhiVector3f = BhiVector3f(x, y, z)

fun bhiQuaternionX(angle: Float): BhiQuaternionf {
    //? if >=1.19.3 {
    return BhiQuaternionf().rotationX(angle)
    //?} else {
    /*return com.mojang.math.Vector3f.XP.rotation(angle)*/
    //?}
}

fun bhiQuaternionZ(angle: Float): BhiQuaternionf {
    //? if >=1.19.3 {
    return BhiQuaternionf().rotationZ(angle)
    //?} else {
    /*return com.mojang.math.Vector3f.ZP.rotation(angle)*/
    //?}
}

fun BhiQuaternionf.bhiTransform(vector: BhiVector3f) {
    //? if >=1.19.3 {
    this.transform(vector)
    //?} else {
    /*vector.transform(this)*/
    //?}
}

fun BhiVector3f.bhiX(): Float = this.x()
fun BhiVector3f.bhiY(): Float = this.y()
fun BhiVector3f.bhiZ(): Float = this.z()

fun BhiVector3f.bhiLength(): Float {
    //? if >=1.19.3 {
    return this.length()
    //?} else {
    /*return kotlin.math.sqrt(x() * x() + y() * y() + z() * z())*/
    //?}
}

fun bhiTransformPosition(matrix: BhiMatrix4f, x: Float, y: Float, z: Float): BhiVector3f {
    //? if >=1.19.3 {
    return matrix.transformPosition(BhiVector3f(x, y, z))
    //?} else {
    /*val vector = com.mojang.math.Vector4f(x, y, z, 1.0f)
    vector.transform(matrix)
    return BhiVector3f(vector.x(), vector.y(), vector.z())*/
    //?}
}
