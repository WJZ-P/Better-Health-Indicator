pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "LegacyFabric"
            url = uri("https://repo.legacyfabric.net/repository/legacyfabric/")
        }
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases/")
        }
        maven {
            name = "Forge"
            url = uri("https://maven.minecraftforge.net/")
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
        // 1.13.2 及更早版本使用 Legacy Fabric 的 Intermediary 与 Yarn。
        versions("1.12.2", "1.13.2").buildscript("build-legacy.gradle.kts")

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

        // Stonecutter 的项目标识必须唯一，但实际 Minecraft 版本仍可相同。
        // 因此 NeoForge 节点使用带加载器前缀的项目名，同时继续按 26.1.2
        // 处理共享源码中的版本条件。
        version("neoforge-26.1.2", "26.1.2").buildscript("build-neoforge.gradle.kts")
        version("neoforge-1.21.11", "1.21.11").buildscript("build-neoforge.gradle.kts")
        version("neoforge-1.21.1", "1.21.1").buildscript("build-neoforge.gradle.kts")
        version("forge-26.1.2", "26.1.2").buildscript("build-forge.gradle.kts")
        version("forge-1.21.11", "1.21.11").buildscript("build-forge.gradle.kts")
        version("forge-1.21.1", "1.21.1").buildscript("build-forge.gradle.kts")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "better-health-indicator"
