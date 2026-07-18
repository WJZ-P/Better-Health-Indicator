import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.15-SNAPSHOT"
    id("legacy-looming") version "1.15-SNAPSHOT"
    java
    `maven-publish`
}

stonecutter {
    constants.put("neoforge", false)
}

fun prop(key: String): String = project.property(key) as String

val minecraftVersion = stonecutter.current.version
val manualLibDir = layout.projectDirectory.dir("../../.gradle/manual-libs")

version = "${prop("mod_version")}+mc$minecraftVersion"
group = prop("mod_group")

base {
    archivesName.set("${prop("mod_id")}-legacy-fabric-$minecraftVersion")
}

repositories {
    ivy {
        name = "LocalLegacyMappings"
        url = manualLibDir.asFile.toURI()
        patternLayout {
            artifact("[artifact]-[revision](-[classifier]).[ext]")
        }
        metadataSources {
            artifact()
        }
    }
    maven {
        name = "LegacyFabric"
        url = uri("https://repo.legacyfabric.net/repository/legacyfabric/")
    }
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    "mappings"("net.legacyfabric:yarn:$minecraftVersion+build.${prop("yarn_build")}:v2")
    "modImplementation"("net.fabricmc:fabric-loader:${prop("loader_version")}")
}

sourceSets.named("main") {
    java.setSrcDirs(listOf(layout.projectDirectory.dir("../../src/legacyfabric/java")))
    resources.setSrcDirs(listOf(layout.projectDirectory.dir("../../src/legacyfabric/resources")))
}

tasks.processResources {
    val values = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "loader_version" to prop("loader_version"),
    )
    inputs.properties(values)
    filesMatching("fabric.mod.json") {
        expand(values)
    }
    from(layout.projectDirectory.file("../../src/main/resources/assets/better_health_indicator/icon.png")) {
        into("assets/better_health_indicator")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}
