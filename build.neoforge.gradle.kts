// NeoForge (1.20.6〜26.3) クライアント MOD
import java.util.Properties

plugins {
    `java`
    id("net.neoforged.moddev") version "2.0.148"
}

val localProps = file("gradle.properties")
if (localProps.exists()) {
    val props = Properties()
    localProps.reader(Charsets.UTF_8).use { props.load(it) }
    for (name in props.stringPropertyNames()) {
        project.extensions.extraProperties.set(name.trim('\uFEFF'), props.getProperty(name))
    }
}

sc.constants["fabric"] = false
sc.constants["neoforge"] = true
sc.constants["forge"] = false

val minecraftVersion: String = sc.current.version
val javaVer = if (sc.eval(minecraftVersion, ">=26.1")) 25 else 21

// JourneyMap API 2.0 系（journeymap.api.v2）の配布が無い構成（1.20.6 等）は jm_api_version を置かない。
// その構成では v2 API を使う jm/ パッケージと compat/JmCompat.java をコンパイルから外す
// （JourneyMap との連携は map/jm の JourneyMapLegacyAdapter がリフレクションで担うので、機能は落ちない）
if (project.findProperty("jm_api_version") == null) {
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
version = "${rootProject.version}+$minecraftVersion-neoforge"

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com") { name = "BlameJared" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

neoForge {
    version = project.property("neoforge_version").toString()
    runs {
        create("client") {
            client()
        }
    }
    mods {
        create("mapscribble") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // JourneyMap API: compile time only. Never bundled in the jar.
    // jm_api_version は versions/<構成>/gradle.properties に置く。
    // API 2.0 系の配布が無い構成（1.20.6 等）は置かず、この行を飛ばす
    project.findProperty("jm_api_version")?.let {
        compileOnly("info.journeymap:journeymap-api-neoforge:$it") { isTransitive = false }
    }
    // JourneyMap 本体: 開発用クライアントでだけ読み込む（mod の jar なので runtimeOnly で run に載る。MOD の jar には入らない）。
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
    exclude("META-INF/mods.toml", "fabric.mod.json")
    val props = mapOf(
        "version" to rootProject.version.toString(),
        "minecraft" to minecraftVersion,
        // Minecraft の対応範囲（Maven 形式。versions/<構成>/gradle.properties に置く）。
        // この構成向けの jar が別のマイナー版で読み込まれないよう、上限を持つ範囲を入れる
        "mc_version_range" to project.property("mc_version_range").toString(),
        // NeoForge 自体の範囲は下限だけにする。上限を入れると、将来のローダー更新で読み込めなくなる
        "loader" to project.property("neoforge_version").toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("META-INF/*.toml") {
        expand(props)
    }
}
