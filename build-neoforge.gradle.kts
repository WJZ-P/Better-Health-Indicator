import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.3.21"
    id("net.neoforged.moddev") version "2.0.142"
}

stonecutter {
    constants.put("fabric", false)
    constants.put("forge", false)
    constants.put("neoforge", true)
    constants.put("forge_like", true)
}

val versionProperties = Properties().apply {
    project.file("gradle.properties").inputStream().use(::load)
}

fun versionProp(key: String): String =
    versionProperties.getProperty(key) ?: error("Missing NeoForge version property '$key' in ${project.path}")

fun commonProp(key: String): String = project.property(key) as String

val minecraftVersion = versionProp("minecraft_version")
val minecraftVersionRange = versionProp("minecraft_version_range")
val neoForgeVersion = versionProp("neo_version")
val neoForgeVersionRange = versionProp("neo_version_range")
val kotlinForForgeVersion = versionProp("kotlin_for_forge_version")
val kotlinForForgeLoaderRange = versionProp("kotlin_for_forge_loader_range")
val clothConfigVersion = versionProp("cloth_config_version")
val requiredJava = versionProp("java_version").toInt()
val modId = commonProp("mod_id")

version = "${commonProp("mod_version")}+mc$minecraftVersion"
group = commonProp("mod_group")

base {
    archivesName.set("$modId-neoforge-$minecraftVersion")
}

repositories {
    maven {
        name = "KotlinForForge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content {
            includeGroup("thedarkcolour")
        }
    }
    maven {
        name = "Shedaniel"
        url = uri("https://maven.shedaniel.me/")
    }
    mavenCentral()
}

val neoForgeMain = rootProject.layout.projectDirectory.dir("src/neoforge")

sourceSets {
    named("main") {
        // 共享源码由 Stonecutter 预处理并自动加入；这里只追加平台实现。
        java.srcDir(neoForgeMain.dir("java"))
        resources.srcDir(neoForgeMain.dir("resources"))
        resources.exclude("fabric.mod.json")
    }
}

kotlin {
    jvmToolchain(requiredJava)
    sourceSets.named("main") {
        kotlin.srcDir(neoForgeMain.dir("kotlin"))
        // 这两个实现属于 Fabric；NeoForge 提供自己的入口。
        kotlin.exclude("com/wjz/betterhealthindicator/BetterHealthIndicatorClient.kt")
        kotlin.exclude("com/wjz/betterhealthindicator/client/gui/ModMenuIntegration.kt")
    }
}

neoForge {
    // 本项目只依赖可编译/运行的补丁后字节码，不需要反编译 Minecraft 源码。
    // 使用官方二进制补丁管线可显著降低首次配置的耗时和内存占用。
    enable {
        version = neoForgeVersion
        setDisableRecompilation(true)
    }

    runs {
        create("client") {
            client()
            gameDirectory = rootProject.layout.projectDirectory.dir("run/neoforge-$minecraftVersion").asFile
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

val localRuntime = configurations.create("localRuntime")

configurations.named("runtimeClasspath") {
    extendsFrom(localRuntime)
}

dependencies {
    // Kotlin 运行库由玩家统一安装的 KotlinForForge 提供，避免每个 Mod 重复内嵌。
    add("implementation", "thedarkcolour:kotlinforforge-neoforge:$kotlinForForgeVersion")

    // Cloth Config 是可选依赖：安装后，NeoForge 的 Mods 页面会出现配置按钮。
    add("compileOnly", "me.shedaniel.cloth:cloth-config-neoforge:$clothConfigVersion")
    add(localRuntime.name, "me.shedaniel.cloth:cloth-config-neoforge:$clothConfigVersion")
}

tasks.processResources {
    val replaceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoForgeVersion,
        "neo_version_range" to neoForgeVersionRange,
        "kotlin_for_forge_loader_range" to kotlinForForgeLoaderRange,
        "cloth_config_version" to clothConfigVersion,
        "java_version" to requiredJava,
        "mod_id" to modId,
        "mod_name" to commonProp("mod_name"),
        "mod_license" to commonProp("mod_license"),
        "mod_author" to commonProp("mod_author"),
        "mod_description" to commonProp("mod_description"),
    )

    inputs.properties(replaceProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
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
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(requiredJava.toString()))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(requiredJava))
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
