import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.KotlinModuleMetadataTransformer
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.3.21"
    id("net.minecraftforge.gradle") version "[7.0.17,8)"
    id("net.minecraftforge.renamer") version "1.1.5"
    id("com.gradleup.shadow") version "9.6.0"
}

stonecutter {
    constants.put("fabric", false)
    constants.put("forge", true)
    constants.put("neoforge", false)
    constants.put("netease", true)
    constants.put("forge_like", true)
}

val versionProperties = Properties().apply {
    project.file("gradle.properties").inputStream().use(::load)
}

fun versionProp(key: String): String =
    versionProperties.getProperty(key) ?: error("Missing NetEase version property '$key' in ${project.path}")

fun commonProp(key: String): String = project.property(key) as String

val neteaseVersion = versionProp("netease_version")
val minecraftVersion = versionProp("minecraft_version")
val minecraftVersionRange = versionProp("minecraft_version_range")
val forgeVersion = versionProp("forge_version")
val forgeVersionRange = versionProp("forge_version_range")
val forgeLoaderRange = versionProp("forge_loader_range")
val requiredJava = versionProp("java_version").toInt()
val mavenizerOffline = System.getenv("BHI_MAVENIZER_OFFLINE")?.toBoolean() ?: false
val buildJava = requiredJava.coerceAtLeast(17)
val resourcePackFormat = versionProp("resource_pack_format").toInt()
val modId = commonProp("mod_id")
val relocatedKotlinPackage = "${commonProp("mod_group")}.betterhealthindicator.internal.kotlin"
val requiresProductionRemap = minecraftVersion in setOf("1.18.1", "1.19.2", "1.20.1")

version = "${commonProp("mod_version")}+netease$neteaseVersion"
group = commonProp("mod_group")

base {
    archivesName.set("$modId-netease-$neteaseVersion")
}

minecraft {
    mappings("official", minecraftVersion)
    if (mavenizerOffline) {
        mavenizerArguments.add("--offline")
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

val neteaseMain = rootProject.layout.projectDirectory.dir("src/netease")

sourceSets {
    named("main") {
        java.srcDir(neteaseMain.dir("java"))
        resources.srcDir(neteaseMain.dir("resources"))
        resources.exclude("fabric.mod.json")
    }
}

kotlin {
    jvmToolchain(buildJava)
    sourceSets.named("main") {
        kotlin.srcDir(neteaseMain.dir("kotlin"))
        kotlin.exclude("com/wjz/betterhealthindicator/BetterHealthIndicatorClient.kt")
        kotlin.exclude("com/wjz/betterhealthindicator/client/gui/ModMenuIntegration.kt")
        kotlin.exclude("com/wjz/betterhealthindicator/client/gui/ClothConfigScreenFactory.kt")
    }
}

// 仅此配置会被合并进网易发行 Jar。Forge/Minecraft 自身绝不参与 Shadow。
val relocatedKotlin = configurations.create("relocatedKotlin")
configurations.named("compileClasspath") {
    extendsFrom(relocatedKotlin)
}
configurations.named("runtimeClasspath") {
    extendsFrom(relocatedKotlin)
}

// Forge 1.16 的运行时是 Java 8，但 Gradle 9 至少需要 Java 17。
// 解析 Forge 依赖时仍按游戏所需 Java 版本声明 consumer attribute。
configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, requiredJava)
    }
}

dependencies {
    add("implementation", minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
    add(relocatedKotlin.name, "org.jetbrains.kotlin:kotlin-stdlib:2.3.21") {
        isTransitive = false
    }
    add("annotationProcessor", "org.spongepowered:mixin:0.8.7:processor")
}

// Forge 1.20.4 及更早版本在生产环境使用 SRG 名称。开发命名的 Jar 必须经过
// Renamer 才能放进网易客户端；同时让 Mixin AP 生成对应的 refmap。
if (requiresProductionRemap) {
    renamer.mappings(minecraft.dependency.toSrgFile)

    val mixinConfig = renamer.enableMixinRefmaps {
        config("better_health_indicator.mixins.json")
        refMap.set("better_health_indicator.refmap.json")
        source(sourceSets.named("main").get())
    }
    mixinConfig.jar(tasks.named<Jar>("shadowJar"))
}

tasks.processResources {
    val replaceProperties = mapOf(
        "version" to project.version,
        "netease_version" to neteaseVersion,
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "forge_version" to forgeVersion,
        "forge_version_range" to forgeVersionRange,
        "forge_loader_range" to forgeLoaderRange,
        "resource_pack_format" to resourcePackFormat,
        "java_version" to requiredJava,
        "mod_id" to modId,
        "mod_name" to commonProp("mod_name"),
        "mod_license" to commonProp("mod_license"),
        "mod_author" to commonProp("mod_author"),
        "mod_description" to commonProp("mod_description"),
    )

    inputs.properties(replaceProperties)
    inputs.property("includeMixinRefmap", requiresProductionRemap)
    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
    filesMatching("better_health_indicator.mixins.json") {
        expand(replaceProperties)
        if (requiresProductionRemap) {
            filter { line: String ->
                if (line.contains("\"package\":")) {
                    "$line\n  \"refmap\": \"better_health_indicator.refmap.json\","
                } else {
                    line
                }
            }
        }
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
            "BHI-Distribution" to "NetEase Java Edition",
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set(if (requiresProductionRemap) "dev" else "")
    configurations = listOf(relocatedKotlin)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    // Shadow 会同时改写本 Mod 字节码中对 kotlin.* 的引用，
    // 因此每个网易 Jar 拥有自己的隔离运行库，不会和其他 Mod 抢类。
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
            "BHI-Distribution" to "NetEase Java Edition",
            "BHI-Relocated-Kotlin" to relocatedKotlinPackage,
        )
    }
}

val productionJar = if (requiresProductionRemap) {
    renamer.classes("renameShadowJar", tasks.named<ShadowJar>("shadowJar")) {
        archiveClassifier.set("")
    }
} else {
    tasks.named<ShadowJar>("shadowJar")
}

tasks.assemble {
    dependsOn(productionJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(if (requiredJava == 8) "1.8" else requiredJava.toString()))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(buildJava))
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
