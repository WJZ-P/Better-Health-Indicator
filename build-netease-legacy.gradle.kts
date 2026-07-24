import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
    id("net.minecraftforge.gradle") version "[7.0.17,8)"
    id("net.minecraftforge.renamer") version "1.1.5"
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
val resourcePackFormat = versionProp("resource_pack_format").toInt()
val mavenizerOffline = System.getenv("BHI_MAVENIZER_OFFLINE")?.toBoolean() ?: false
val modId = commonProp("mod_id")

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

val legacySource = rootProject.layout.projectDirectory.dir("src/neteaselegacy")
val legacyCommonSource = rootProject.layout.projectDirectory.dir("src/neteaselegacy-common")
val neteaseResources = rootProject.layout.projectDirectory.dir("src/netease/resources")

sourceSets.named("main") {
    java.setSrcDirs(listOf(legacySource.dir("java"), legacyCommonSource.dir("java")))
    resources.setSrcDirs(listOf(neteaseResources))
}

configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, requiredJava)
    }
}

dependencies {
    add("implementation", minecraft.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
}

renamer.mappings(minecraft.dependency.toSrgFile)

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
    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
    filesMatching("pack.mcmeta") {
        expand(replaceProperties)
    }
    from(rootProject.layout.projectDirectory.dir("src/main/resources/assets")) {
        into("assets")
    }
}

tasks.jar {
    archiveClassifier.set("dev")
    manifest {
        attributes(
            "Implementation-Title" to commonProp("mod_name"),
            "Implementation-Version" to project.version,
            "BHI-Distribution" to "NetEase Java Edition (legacy)",
        )
    }
}

val productionJar = renamer.classes("renameJar", tasks.named<Jar>("jar")) {
    archiveClassifier.set("")
}

tasks.assemble {
    dependsOn(productionJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(requiredJava.coerceAtLeast(17)))
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
