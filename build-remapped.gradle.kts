import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Minecraft 1.21.11 及更早版本仍需要 Loom 执行生产环境重映射。
    id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
    kotlin("jvm") version "2.3.21"
    `maven-publish`
}

fun prop(key: String): String = project.property(key) as String

val minecraftVersion = stonecutter.current.version
val requiredJava = prop("java_version").toInt()

version = "${prop("mod_version")}+mc$minecraftVersion"
group = prop("mod_group")

base {
    archivesName.set("${prop("mod_id")}-fabric-$minecraftVersion")
}

repositories {
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
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
    "modImplementation"("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")

    "modImplementation"("me.shedaniel.cloth:cloth-config-fabric:${prop("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    "modImplementation"("maven.modrinth:modmenu:${prop("modmenu_version")}")
}

tasks.processResources {
    val replaceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "minecraft_dependency" to prop("minecraft_dependency"),
        "java_version" to requiredJava,
        "loader_version" to prop("loader_version"),
        "fabric_api_version" to prop("fabric_api_version"),
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
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(requiredJava.toString()))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(requiredJava))
    withSourcesJar()
}
