import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.attributes.java.TargetJvmVersion
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.3.21"
    id("net.minecraftforge.gradle") version "[7.0.17,8)"
}

stonecutter {
    constants.put("fabric", false)
    constants.put("forge", true)
    constants.put("neoforge", false)
    constants.put("netease", false)
    constants.put("forge_like", true)
}

val versionProperties = Properties().apply {
    project.file("gradle.properties").inputStream().use(::load)
}

fun versionProp(key: String): String =
    versionProperties.getProperty(key) ?: error("Missing Forge version property '$key' in ${project.path}")

fun commonProp(key: String): String = project.property(key) as String

val minecraftVersion = versionProp("minecraft_version")
val minecraftVersionRange = versionProp("minecraft_version_range")
val forgeVersion = versionProp("forge_version")
val forgeVersionRange = versionProp("forge_version_range")
val forgeLoaderRange = versionProp("forge_loader_range")
val kotlinForForgeVersion = versionProp("kotlin_for_forge_version")
val kotlinForForgeLoaderRange = versionProp("kotlin_for_forge_loader_range")
val clothConfigVersion = versionProp("cloth_config_version")
val requiredJava = versionProp("java_version").toInt()
val bytecodeJava = versionProperties.getProperty("bytecode_java_version")?.toInt() ?: requiredJava
val resourcePackFormat = versionProp("resource_pack_format").toInt()
val resourcePackFormatMinor = versionProperties.getProperty("resource_pack_format_minor")?.toInt()
val modId = commonProp("mod_id")

version = "${commonProp("mod_version")}+mc$minecraftVersion"
group = commonProp("mod_group")

base {
    archivesName.set("$modId-forge-$minecraftVersion")
}

minecraft {
    if (minecraftVersion != "26.1.2") {
        mappings("official", minecraftVersion)
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
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

val forgeMain = rootProject.layout.projectDirectory.dir("src/forge")

sourceSets {
    named("main") {
        java.srcDir(forgeMain.dir("java"))
        resources.srcDir(forgeMain.dir("resources"))
        resources.exclude("fabric.mod.json")
    }
}

kotlin {
    jvmToolchain(requiredJava)
    sourceSets.named("main") {
        kotlin.srcDir(forgeMain.dir("kotlin"))
        kotlin.exclude("com/wjz/betterhealthindicator/BetterHealthIndicatorClient.kt")
        kotlin.exclude("com/wjz/betterhealthindicator/client/gui/ModMenuIntegration.kt")
        if (minecraftVersion != "1.21.1") {
            kotlin.exclude("com/wjz/betterhealthindicator/client/gui/ClothConfigScreenFactory.kt")
        }
    }
}

val localRuntime = configurations.create("localRuntime")

configurations.named("runtimeClasspath") {
    extendsFrom(localRuntime)
}

// Gradle normally infers the consumer JVM from the emitted bytecode target. For
// 26.1.2 those deliberately differ: Forge runs on Java 25 while this mod emits
// Java 21 bytecode so Mixin 0.8.7 can transform it.
configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, requiredJava)
    }
}

dependencies {
    add("implementation", minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
    add("implementation", "thedarkcolour:kotlinforforge:$kotlinForForgeVersion")

    if (minecraftVersion == "1.21.1") {
        add("compileOnly", "me.shedaniel.cloth:cloth-config-forge:$clothConfigVersion")
        add(localRuntime.name, "me.shedaniel.cloth:cloth-config-forge:$clothConfigVersion")
    }

    add("annotationProcessor", "org.spongepowered:mixin:0.8.7:processor")
    if (minecraftVersion != "1.21.1") {
        add("annotationProcessor", "net.minecraftforge:eventbus-validator:7.0.1")
    }
}

tasks.processResources {
    val clothConfigDependency = if (minecraftVersion == "1.21.1") {
        """
        [[dependencies.$modId]]
        modId="cloth_config"
        mandatory=false
        versionRange="[$clothConfigVersion,)"
        ordering="AFTER"
        side="CLIENT"
        """.trimIndent()
    } else {
        ""
    }

    val replaceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "forge_version" to forgeVersion,
        "forge_version_range" to forgeVersionRange,
        "forge_loader_range" to forgeLoaderRange,
        "kotlin_for_forge_loader_range" to kotlinForForgeLoaderRange,
        "cloth_config_version" to clothConfigVersion,
        "cloth_config_dependency" to clothConfigDependency,
        "resource_pack_metadata" to if (resourcePackFormatMinor == null) {
            "\"pack_format\": $resourcePackFormat"
        } else {
            "\"min_format\": [$resourcePackFormat, $resourcePackFormatMinor],\n" +
                "    \"max_format\": [$resourcePackFormat, $resourcePackFormatMinor]"
        },
        // Mixin 0.8.7 bundled by current Forge only recognises compatibility
        // levels through JAVA_21. The game may still require a newer toolchain.
        "java_version" to bytecodeJava,
        "mod_id" to modId,
        "mod_name" to commonProp("mod_name"),
        "mod_license" to commonProp("mod_license"),
        "mod_author" to commonProp("mod_author"),
        "mod_description" to commonProp("mod_description"),
    )

    inputs.properties(replaceProperties)
    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
    filesMatching("better_health_indicator.mixins.json") {
        expand(replaceProperties)
    }
    filesMatching("pack.mcmeta") {
        expand(replaceProperties)
    }
}

tasks.jar {
    manifest {
        attributes(
            "MixinConfigs" to "better_health_indicator.mixins.json",
            "Implementation-Title" to commonProp("mod_name"),
            "Implementation-Version" to project.version,
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(bytecodeJava)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(bytecodeJava.toString()))
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
