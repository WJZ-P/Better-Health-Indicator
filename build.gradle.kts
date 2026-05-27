plugins {
    kotlin("jvm") version "2.3.21"
    id("net.fabricmc.fabric-loom") version "1.15.5"
}

val javaVersion = 25
val modId = property("mod_id").toString()
val modName = property("mod_name").toString()
val modVersion = property("mod_version").toString()
val modGroup = property("mod_group").toString()
val modAuthor = property("mod_author").toString()
val modDescription = property("mod_description").toString()
val minecraftVersion = property("minecraft_version").toString()
val loaderVersion = property("loader_version").toString()
val fabricApiVersion = property("fabric_api_version").toString()
val fabricKotlinVersion = property("fabric_kotlin_version").toString()

group = modGroup
version = modVersion

base {
    archivesName.set(modId)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")

    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }

    withSourcesJar()
}

kotlin {
    jvmToolchain(javaVersion)
}

tasks.processResources {
    inputs.properties(
        "java_version" to javaVersion,
        "minecraft_version" to minecraftVersion,
        "loader_version" to loaderVersion,
        "fabric_api_version" to fabricApiVersion,
        "fabric_kotlin_version" to fabricKotlinVersion,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_version" to modVersion,
        "mod_author" to modAuthor,
        "mod_description" to modDescription,
    )

    filesMatching("fabric.mod.json") {
        expand(inputs.properties)
    }
}

tasks.withType<Jar>().configureEach {
    from("LICENSE") {
        rename { "${it}_${modId}" }
    }
}
