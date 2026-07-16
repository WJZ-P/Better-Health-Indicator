plugins {
    id("dev.kikugie.stonecutter")
}

// src/ 中始终保存 VCS 基准版本。切换版本时 Stonecutter 会生成对应的版本源码。
stonecutter active "26.1.2" /* [SC] DO NOT EDIT */

stonecutter parameters {
    // 可在源码中使用：//? if minecraft_26_1_or_newer
    constants["minecraft_26_1_or_newer"] = eval(current.version, ">=26.1")
    constants["minecraft_1_21_5_or_newer"] = eval(current.version, ">=1.21.5")
    constants["minecraft_1_21_or_newer"] = eval(current.version, ">=1.21")
}

tasks.register("buildAllVersions") {
    group = "build"
    description = "Builds every declared Minecraft version."
    dependsOn(subprojects.map { "${it.path}:build" })
}
