pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    kotlinController = true
    create(rootProject) {
        // 1.21.11 及更早版本仍需要从官方名称重映射到生产环境名称。
        versions(
            "1.14.4",
            "1.15.2",
            "1.16.5",
            "1.17.1",
            "1.18.2",
            "1.19.4",
            "1.20.1",
            "1.20.4",
            "1.21.1",
            "1.21.4",
            "1.21.8",
            "1.21.11",
        ).buildscript("build-remapped.gradle.kts")

        // 26.1 起 Minecraft 官方发布物不再混淆，使用非重映射 Loom。
        versions("26.1.2", "26.2").buildscript("build.gradle.kts")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "better-health-indicator"
