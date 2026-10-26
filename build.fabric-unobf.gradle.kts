// Fabric (26.1+ 非難読化) クライアント MOD
import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
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

// JourneyMap API 2.0 系（journeymap.api.v2）の配布が無い構成は jm_api_version を置かない。
// その構成では v2 API を使う jm/ パッケージと compat/JmCompat.java をコンパイルから外す
// （JourneyMap との連携は map/jm の JourneyMapLegacyAdapter がリフレクションで担うので、機能は落ちない）
val hasJmApi = project.findProperty("jm_api_version") != null
if (!hasJmApi) {
    sourceSets.main {
        java {
            exclude(
                "io/github/radishsprouts/mapscribble/jm/**",
                "io/github/radishsprouts/mapscribble/compat/JmCompat.java"
            )
        }
    }
}

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

    // JourneyMap API: compile time only. Never bundled in the jar.
    // jm_api_version は versions/<構成>/gradle.properties に置く。
    // API 2.0 系の配布が無い構成は置かず、この行を飛ばす
    project.findProperty("jm_api_version")?.let {
        compileOnly("info.journeymap:journeymap-api-fabric:$it") { isTransitive = false }
    }

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
        // fabric.mod.json の depends.minecraft へ入れる範囲。Fabric の `~x.y.z` は
        // 「>=x.y.z かつ次のマイナー未満」なので、この構成のパッチ更新では読み込めるが、
        // 別のマイナー版では読み込まれない（版そのものを入れると、26.3.1 のようなパッチ版で弾かれる）
        "minecraft" to "~$minecraftVersion",
        "loader" to providers.gradleProperty("loader_version").get(),
        "java" to javaVer.toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    inputs.property("has_jm_api", hasJmApi)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
    // fabric.mod.json は厳密な JSON なので条件付きの記述を置けない。JourneyMap API 2.0 系を使わない構成では
    // journeymap エントリポイントのクラス（jm/MapScribblePlugin）がコンパイルされず、残すと JourneyMap 側から
    // 呼ばれて落ちる。そこで展開後の fabric.mod.json から journeymap エントリポイントを取り除く。
    // jm_api_version が有る構成では何もしない（出力は今までと一字も変わらない）
    if (!hasJmApi) {
        doLast {
            val modJson = File(destinationDir, "fabric.mod.json")
            @Suppress("UNCHECKED_CAST")
            val root = JsonSlurper().parseText(modJson.readText(Charsets.UTF_8)) as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val entrypoints = root["entrypoints"] as? MutableMap<String, Any?>
            entrypoints?.remove("journeymap")
            val generator = JsonGenerator.Options().disableUnicodeEscaping().build()
            modJson.writeText(JsonOutput.prettyPrint(generator.toJson(root)) + "\n", Charsets.UTF_8)
        }
    }
}
