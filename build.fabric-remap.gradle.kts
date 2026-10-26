// Fabric (1.20.1〜1.21.11 難読化・Mojang公式マッピング) クライアント MOD
import java.util.Properties

plugins {
    `java`
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

val localProps = file("gradle.properties")
if (localProps.exists()) {
    val props = Properties()
    localProps.reader(Charsets.UTF_8).use { props.load(it) }
    for (name in props.stringPropertyNames()) {
        project.extensions.extraProperties.set(name.trim('\uFEFF'), props.getProperty(name))
    }
}

sc.constants["fabric"] = true
sc.constants["neoforge"] = false
sc.constants["forge"] = false

val minecraftVersion: String = sc.current.version
val javaVer = if (sc.eval(minecraftVersion, ">=1.21")) 21 else 17

base.archivesName.set("mapscribble")
version = "${rootProject.version}+$minecraftVersion-fabric"

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com") { name = "BlameJared" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // JourneyMap API: compile time only. Never bundled in the jar
    modCompileOnly("info.journeymap:journeymap-api-fabric:${project.property("jm_api_version")}") { isTransitive = false }
    // JourneyMap 本体: 開発用クライアントでだけ読み込む（jar には入らない）。
    // jm_version は versions/<構成>/gradle.properties に置く。JourneyMap の配布が無い構成は置かず、この行を飛ばす
    project.findProperty("jm_version")?.let { "modLocalRuntime"("maven.modrinth:journeymap:$it") }

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
        // 別のマイナー版では読み込まれない（版そのものを入れると、1.20.2 のようなパッチ版で弾かれる）
        "minecraft" to "~$minecraftVersion",
        "loader" to providers.gradleProperty("loader_version").get(),
        "java" to javaVer.toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
