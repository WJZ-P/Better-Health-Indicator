import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.KotlinModuleMetadataTransformer
import org.gradle.api.file.DuplicatesStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.3.21"
    id("net.neoforged.moddev") version "2.0.142"
    id("com.gradleup.shadow") version "9.6.0"
}

stonecutter {
    constants.put("fabric", false)
    constants.put("forge", false)
    constants.put("neoforge", true)
    constants.put("netease", true)
    constants.put("forge_like", true)
}

val versionProperties = Properties().apply {
    project.file("gradle.properties").inputStream().use(::load)
}

fun versionProp(key: String): String =
    versionProperties.getProperty(key) ?: error("Missing NetEase NeoForge version property '$key' in ${project.path}")

fun commonProp(key: String): String = project.property(key) as String

val neteaseVersion = versionProp("netease_version")
val minecraftVersion = versionProp("minecraft_version")
val minecraftVersionRange = versionProp("minecraft_version_range")
val neoForgeVersion = versionProp("neo_version")
val neoForgeVersionRange = versionProp("neo_version_range")
val fmlLoaderRange = versionProp("fml_loader_range")
val requiredJava = versionProp("java_version").toInt()
val resourcePackFormat = versionProp("resource_pack_format").toInt()
val modId = commonProp("mod_id")
val relocatedKotlinPackage = "${commonProp("mod_group")}.betterhealthindicator.internal.kotlin"

version = "${commonProp("mod_version")}+netease$neteaseVersion"
group = commonProp("mod_group")

base {
    archivesName.set("$modId-netease-$neteaseVersion")
}

repositories {
    mavenCentral()
}

val neteaseNeoForgeMain = rootProject.layout.projectDirectory.dir("src/netease-neoforge")

sourceSets {
    named("main") {
        // Stonecutter provides the shared sources; this directory only contains
        // the self-contained NetEase/NeoForge bootstrap and metadata.
        java.srcDir(neteaseNeoForgeMain.dir("java"))
        resources.srcDir(neteaseNeoForgeMain.dir("resources"))
        resources.exclude("fabric.mod.json")
    }
}

kotlin {
    jvmToolchain(requiredJava)
    sourceSets.named("main") {
        kotlin.srcDir(neteaseNeoForgeMain.dir("kotlin"))
        kotlin.exclude("com/wjz/betterhealthindicator/BetterHealthIndicatorClient.kt")
        kotlin.exclude("com/wjz/betterhealthindicator/client/gui/ModMenuIntegration.kt")
        kotlin.exclude("com/wjz/betterhealthindicator/client/gui/ClothConfigScreenFactory.kt")
    }
}

neoForge {
    enable {
        version = neoForgeVersion
        setDisableRecompilation(true)
    }

    runs {
        create("client") {
            client()
            gameDirectory = rootProject.layout.projectDirectory.dir("run/netease-neoforge-$minecraftVersion").asFile
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Only Kotlin's standard library enters the distribution JAR. Minecraft and
// NeoForge remain loader-provided dependencies and must never be shadowed.
val relocatedKotlin = configurations.create("relocatedKotlin")
configurations.named("compileClasspath") {
    extendsFrom(relocatedKotlin)
}
configurations.named("runtimeClasspath") {
    extendsFrom(relocatedKotlin)
}

dependencies {
    add(relocatedKotlin.name, "org.jetbrains.kotlin:kotlin-stdlib:2.3.21") {
        isTransitive = false
    }
}

tasks.processResources {
    val replaceProperties = mapOf(
        "version" to project.version,
        "netease_version" to neteaseVersion,
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoForgeVersion,
        "neo_version_range" to neoForgeVersionRange,
        "fml_loader_range" to fmlLoaderRange,
        "resource_pack_format" to resourcePackFormat,
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
    filesMatching("pack.mcmeta") {
        expand(replaceProperties)
    }
}

tasks.jar {
    archiveClassifier.set("thin")
    manifest {
        attributes(
            "MixinConfigs" to "better_health_indicator.mixins.json",
            "Implementation-Title" to commonProp("mod_name"),
            "Implementation-Version" to project.version,
            "BHI-Distribution" to "NetEase Java Edition (NeoForge)",
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    configurations = listOf(relocatedKotlin)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    // Rewrite both the embedded classes and this mod's bytecode references,
    // isolating Kotlin from every other mod in the managed NetEase client.
    relocate("kotlin", relocatedKotlinPackage)
    transform<KotlinModuleMetadataTransformer>()

    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/versions/**/module-info.class",
        "module-info.class",
    )

    manifest {
        attributes(
            "MixinConfigs" to "better_health_indicator.mixins.json",
            "Implementation-Title" to commonProp("mod_name"),
            "Implementation-Version" to project.version,
            "BHI-Distribution" to "NetEase Java Edition (NeoForge)",
            "BHI-Relocated-Kotlin" to relocatedKotlinPackage,
        )
    }
}

tasks.assemble {
    dependsOn(tasks.named<ShadowJar>("shadowJar"))
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
