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

        // 网易开发者平台的数字标签不总是完整的 Minecraft 版本号：
        // 1.20 / 1.18 / 1.16 分别对应 1.20.1 / 1.18.1 / 1.16.4；
        // 1.21 则确实对应 Minecraft 1.21，并使用 Forge/FML 51.0.33。
        // 网易 1.18+ 发行 Jar 内嵌并重定位 Kotlin；1.16 使用无 Kotlin 的完整爱心 Java 后端。
        // 两类成品都不要求玩家在中国版环境另行安装 KotlinForForge。
        // MC Studio 的网易 1.21.8 客户端使用 NeoForge 21.8.52；其余网易版本仍使用 Forge。
        version("netease-1.21.8", "1.21.8").buildscript("build-netease-neoforge.gradle.kts")
        version("netease-1.21", "1.21").buildscript("build-netease.gradle.kts")
        version("netease-1.20.6", "1.20.6").buildscript("build-netease.gradle.kts")
        version("netease-1.20", "1.20.1").buildscript("build-netease.gradle.kts")
        version("netease-1.19.2", "1.19.2").buildscript("build-netease.gradle.kts")
        version("netease-1.18", "1.18.1").buildscript("build-netease.gradle.kts")
        version("netease-1.16", "1.16.4").buildscript("build-netease-legacy.gradle.kts")

        // 网易 1.12.2 / 1.11.2 / 1.7.10 由 legacy/netease-forge 中隔离的
        // Gradle 8.7 构建负责，不能作为当前 Gradle 9.5 的 Stonecutter 子项目加载。
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "better-health-indicator"
