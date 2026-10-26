// Minecraft Forge (1.20.6 以降) クライアント MOD。ForgeGradle 7 を使う。
// 1.20.1 の Forge だけは userdev の形が違うので build.forge.gradle.kts（legacyforge）が担当する。
import java.util.Properties

plugins {
    `java`
    id("net.minecraftforge.gradle") version "7.0.40"
}

val localProps = file("gradle.properties")
if (localProps.exists()) {
    val props = Properties()
    localProps.reader(Charsets.UTF_8).use { props.load(it) }
    for (name in props.stringPropertyNames()) {
        project.extensions.extraProperties.set(name.trim('﻿'), props.getProperty(name))
    }
}

sc.constants["fabric"] = false
sc.constants["neoforge"] = false
sc.constants["forge"] = true

val minecraftVersion: String = sc.current.version
// 26.1 以降の Minecraft は非難読化なので、マッピングの指定が要らない
val obfuscated = !sc.eval(minecraftVersion, ">=26.1")
// Mojang が 26.1 以降で Java 25 を配るので、それに合わせる（1.20.6〜1.21.11 は 21）
val javaVer = if (sc.eval(minecraftVersion, ">=26.1")) 25 else 21

val forgeVersion: String = project.property("forge_version").toString()
// mods.toml の loaderVersion / forge の versionRange に差し込むメジャー版（1.21.11-61.2.4 → 61）
val fmlMajor: String = forgeVersion.substringAfterLast('-').substringBefore('.')

base.archivesName.set("mapscribble")
version = "${rootProject.version}+$minecraftVersion-forge"

val mc = minecraft

repositories {
    mc.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
    maven("https://maven.blamejared.com") { name = "BlameJared" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

minecraft {
    if (obfuscated) {
        mappings("official", minecraftVersion)
    }
    runs {
        create("client") {
            workingDir.set(layout.projectDirectory.dir("run"))
        }
    }
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:$forgeVersion"))

    // JourneyMap API: compile time only. Never bundled in the jar.
    // jm_api_version は versions/<構成>/gradle.properties に置く。
    // API 2.0 系の配布が無い構成（1.20.6 等）は置かず、この行を飛ばす
    project.findProperty("jm_api_version")?.let {
        compileOnly("info.journeymap:journeymap-api-forge:$it") { isTransitive = false }
    }
    // JourneyMap 本体: 開発用クライアントでだけ読み込む（MOD の jar には入らない）。
    // jm_version は versions/<構成>/gradle.properties に置く。JourneyMap の配布が無い構成は置かず、この行を飛ばす
    project.findProperty("jm_version")?.let { runtimeOnly("maven.modrinth:journeymap:$it") }

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.slf4j:slf4j-api:2.0.12")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.12")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVer))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVer)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    exclude("META-INF/neoforge.mods.toml", "fabric.mod.json")
    val props = mapOf(
        "version" to rootProject.version.toString(),
        "minecraft" to minecraftVersion,
        "loader" to forgeVersion,
        // 新しい Forge の mods.toml は loaderVersion / forge の versionRange にメジャー版を使う
        "fml" to fmlMajor,
        "mc_range" to project.property("mc_version_range").toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("META-INF/mods.toml") {
        expand(props)
    }
}
