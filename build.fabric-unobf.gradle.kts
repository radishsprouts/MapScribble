// Fabric (26.1+ 非難読化) クライアント MOD
import java.util.Properties

plugins {
    `java`
    id("net.fabricmc.fabric-loom") version "1.18.2"
}

val localProps = file("gradle.properties")
if (localProps.exists()) {
    val props = Properties()
    localProps.reader(Charsets.UTF_8).use { props.load(it) }
    for (name in props.stringPropertyNames()) {
        project.extensions.extraProperties.set(name.trim('﻿'), props.getProperty(name))
    }
}

sc.constants["fabric"] = true
sc.constants["neoforge"] = false
sc.constants["forge"] = false

val minecraftVersion: String = sc.current.version
val javaVer = 25

base.archivesName.set("mapscribble")
version = "${rootProject.version}+$minecraftVersion-fabric"

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com") { name = "BlameJared" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

// src/gametest: 実際のクライアントを起動する client gametest（`:<構成>:runClientGameTest` で動かす）。
// Fabric API の client gametest API を使うため、Fabric 版の 26.1+ だけで有効にする
// （1.20.1 / 1.21.1 の Fabric API には client gametest API が無く、NeoForge / Forge には Fabric API が無い）。
// 実行ディレクトリは Loom の既定どおり `build/run/clientGameTest`（開発用クライアントの `run/` とは分かれる）
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "mapscribble-test"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")

    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // client gametest はフルの Fabric API で動かす
    "gametestImplementation"("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // JourneyMap API: compile time only. Never bundled in the jar
    compileOnly("info.journeymap:journeymap-api-fabric:${project.property("jm_api_version")}") { isTransitive = false }

    // JourneyMap 本体: 開発用クライアントと client gametest でだけ読み込む（jar には入らない）。
    // jm_version は versions/<構成>/gradle.properties に置く。JourneyMap の配布が無い構成は置かず、この行を飛ばす
    project.findProperty("jm_version")?.let {
        "localRuntime"("maven.modrinth:journeymap:$it")
        "gametestRuntimeOnly"("maven.modrinth:journeymap:$it")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVer))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVer)
}

tasks.withType<JavaExec>().matching { it.name == "runClient" }.configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVer))
    })
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml")
    val props = mapOf(
        "version" to rootProject.version.toString(),
        "minecraft" to minecraftVersion,
        "loader" to providers.gradleProperty("loader_version").get(),
        "java" to javaVer.toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
