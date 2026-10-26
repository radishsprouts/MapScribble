pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MapScribble"

stonecutter {
    create(rootProject) {
        // Fabric (26.1+ 非難読化)
        for (v in listOf("26.1.2", "26.2", "26.3")) {
            version("$v-fabric", v).buildscript("build.fabric-unobf.gradle.kts")
        }
        // Fabric (1.20.1〜1.21.11 難読化・Mojang公式マッピング)
        for (v in listOf("1.20.1", "1.20.6", "1.21.1", "1.21.4", "1.21.11")) {
            version("$v-fabric", v).buildscript("build.fabric-remap.gradle.kts")
        }
        // NeoForge (1.20.6〜26.3)
        for (v in listOf("1.20.6", "1.21.1", "1.21.4", "1.21.11", "26.1.2", "26.2", "26.3")) {
            version("$v-neoforge", v).buildscript("build.neoforge.gradle.kts")
        }
        // 旧 Minecraft Forge (1.20.1)
        version("1.20.1-forge", "1.20.1").buildscript("build.forge.gradle.kts")
        // Minecraft Forge (1.20.6 以降・ForgeGradle 7)
        for (v in listOf("1.20.6", "1.21.11", "26.1.2", "26.2", "26.3")) {
            version("$v-forge", v).buildscript("build.forge-fg7.gradle.kts")
        }

        vcsVersion = "26.1.2-fabric"
    }
}
