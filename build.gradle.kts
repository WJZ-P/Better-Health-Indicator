import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Loom 版本在 settings.gradle 的 pluginManagement 中按 gradle.properties 声明。
    id("net.fabricmc.fabric-loom")
    kotlin("jvm") version "2.3.21"
    `maven-publish`
}

// 从 gradle.properties 读取属性（key 含下划线，用辅助函数避免变量名约束）。
fun prop(key: String): String = project.property(key) as String

version = prop("mod_version")
group = prop("mod_group")

base {
    archivesName.set(prop("mod_id"))
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "Shedaniel"
        url = uri("https://maven.shedaniel.me/")
    }
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/releases/")
    }
    //  光影
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:${prop("minecraft_version")}")

    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${prop("fabric_kotlin_version")}")

    implementation("me.shedaniel.cloth:cloth-config-fabric:${prop("cloth_config_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    implementation("com.terraformersmc:modmenu:${prop("modmenu_version")}")

    runtimeOnly("maven.modrinth:sodium:mc26.1.1-0.8.9-fabric")
    runtimeOnly("maven.modrinth:iris:1.10.9+26.1-fabric")
}

tasks.processResources {
    val replaceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to prop("minecraft_version"),
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
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}
