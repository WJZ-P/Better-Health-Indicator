import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Minecraft 1.21.11 及更早版本仍需要 Loom 执行生产环境重映射。
    id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
    kotlin("jvm") version "2.3.21"
    `maven-publish`
}

stonecutter {
    constants.put("fabric", true)
    constants.put("forge", false)
    constants.put("neoforge", false)
    constants.put("netease", false)
    constants.put("forge_like", false)
}

fun prop(key: String): String = project.property(key) as String

val minecraftVersion = stonecutter.current.version
val requiredJava = prop("java_version").toInt()
val fabricApiModId = project.findProperty("fabric_api_mod_id")?.toString() ?: "fabric-api"
val buildJava = requiredJava.coerceAtLeast(17)
val kotlinJvmTarget = if (requiredJava == 8) "1.8" else requiredJava.toString()
val manualLibDir = layout.projectDirectory.dir("../../.gradle/manual-libs")

version = "${prop("mod_version")}+mc$minecraftVersion"
group = prop("mod_group")

base {
    archivesName.set("${prop("mod_id")}-fabric-$minecraftVersion")
}

repositories {
    // Java TLS 经部分本机代理下载旧依赖时不稳定；允许优先读取由代理预取的本地 Maven 缓存。
    flatDir {
        name = "LocalProxyCache"
        dirs(layout.projectDirectory.dir("../../.gradle/manual-libs"))
    }
    ivy {
        name = "LocalProxyArtifacts"
        url = manualLibDir.asFile.toURI()
        patternLayout {
            artifact("[artifact]-[revision](-[classifier]).[ext]")
        }
        metadataSources {
            artifact()
        }
    }
    maven {
        name = "Shedaniel"
        url = uri("https://maven.shedaniel.me/")
    }
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/releases/")
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"(loom.officialMojangMappings())

    "modImplementation"("net.fabricmc:fabric-loader:${prop("loader_version")}")
    // 这些发行 Jar 已内嵌运行所需模块；优先读代理预取文件，并关闭 Maven 传递解析。
    val fabricApiFile = manualLibDir.file("fabric-api-${prop("fabric_api_version")}.jar").asFile
    val fabricApiModulesDir = manualLibDir.dir("fabric-api-${prop("fabric_api_version")}-modules").asFile
    if (fabricApiModulesDir.isDirectory) {
        "modImplementation"(fileTree(fabricApiModulesDir) { include("**/*.jar") })
    } else if (fabricApiFile.isFile) {
        "modImplementation"(files(fabricApiFile))
    } else {
        "modImplementation"("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}@jar") {
            isTransitive = false
        }
    }
    "modImplementation"("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")

    val clothFile = manualLibDir.file("cloth-config-fabric-${prop("cloth_config_version")}.jar").asFile
    if (clothFile.isFile) {
        "modImplementation"(files(clothFile))
    } else {
        "modImplementation"("me.shedaniel.cloth:cloth-config-fabric:${prop("cloth_config_version")}") {
            isTransitive = false
        }
    }
    val modMenuFile = manualLibDir.file("modmenu-${prop("modmenu_version")}.jar").asFile
    if (modMenuFile.isFile) {
        "modImplementation"(files(modMenuFile))
    } else {
        "modImplementation"("maven.modrinth:modmenu:${prop("modmenu_version")}@jar") {
            isTransitive = false
        }
    }
}

tasks.processResources {
    val replaceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "minecraft_dependency" to prop("minecraft_dependency"),
        "java_version" to requiredJava,
        "loader_version" to prop("loader_version"),
        "fabric_api_version" to prop("fabric_api_version"),
        "fabric_api_mod_id" to fabricApiModId,
        "fabric_kotlin_version" to prop("fabric_kotlin_version"),
        "mod_id" to prop("mod_id"),
        "mod_name" to prop("mod_name"),
        "mod_license" to prop("mod_license"),
        "mod_author" to prop("mod_author"),
        "mod_description" to prop("mod_description"),
    )

    inputs.properties(replaceProperties)
    filesMatching("fabric.mod.json") {
        expand(replaceProperties)
    }
    filesMatching("better_health_indicator.mixins.json") {
        expand(replaceProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(kotlinJvmTarget))
}

java {
    // JDK 17 可通过 --release 生成 Java 16/8 字节码，避免为每个旧版下载一套构建 JDK。
    toolchain.languageVersion.set(JavaLanguageVersion.of(buildJava))
    withSourcesJar()
}

// 1.14 及更早版本没有 PoseStack / MultiBufferSource，改用独立的固定管线后端。
val legacyOpenGlBackend = minecraftVersion in setOf("1.14.4", "1.13.2")
if (legacyOpenGlBackend) {
    kotlin.sourceSets.named("main") {
        kotlin.srcDir(layout.projectDirectory.dir("../../src/legacy14/kotlin"))
        kotlin.exclude(
            "com/wjz/betterhealthindicator/client/compat/GuiGraphicsCompat.kt",
            "com/wjz/betterhealthindicator/client/compat/HudCompat.kt",
            "com/wjz/betterhealthindicator/client/compat/MathCompat.kt",
            "com/wjz/betterhealthindicator/client/compat/WorldRenderCompat.kt",
            "com/wjz/betterhealthindicator/client/gui/ClothConfigScreenFactory.kt",
            "com/wjz/betterhealthindicator/client/hud/HealthPanelHud.kt",
            "com/wjz/betterhealthindicator/client/render/EntityHealthBarRenderer.kt",
            "com/wjz/betterhealthindicator/client/render/EntityModelExtents.kt",
            "com/wjz/betterhealthindicator/client/render/EntitySelector.kt",
            "com/wjz/betterhealthindicator/client/render/HeartGraphics.kt",
            "com/wjz/betterhealthindicator/client/render/HeartLayout.kt",
            "com/wjz/betterhealthindicator/client/render/HeartParticleManager.kt",
            "com/wjz/betterhealthindicator/client/render/LegacyHeartTextures.kt",
            "com/wjz/betterhealthindicator/client/render/LowHealthShake.kt",
            "com/wjz/betterhealthindicator/client/render/TintedHeartTextures.kt",
        )
    }
}
