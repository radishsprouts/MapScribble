// 旧 Minecraft Forge (1.20.1) クライアント MOD
import java.util.Properties

plugins {
    `java`
    id("net.neoforged.moddev.legacyforge") version "2.0.148"
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
sc.constants["neoforge"] = false
sc.constants["forge"] = true

val minecraftVersion: String = sc.current.version

base.archivesName.set("mapscribble")
version = "${rootProject.version}+$minecraftVersion-forge"

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com") { name = "BlameJared" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
}

legacyForge {
    version = project.property("forge_version").toString()
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
    // JourneyMap API: compile time only. Never bundled in the jar
    compileOnly("info.journeymap:journeymap-api-forge:${project.property("jm_api_version")}") { isTransitive = false }
    // JourneyMap 本体: 開発用クライアントでだけ読み込む（MOD の jar には入らない）。
    // 旧 Forge の mod の jar は難読化名なので、開発名へ自動で変換される mod 系の configuration に入れる。
    // jm_version は versions/<構成>/gradle.properties に置く。JourneyMap の配布が無い構成は置かず、この行を飛ばす
    project.findProperty("jm_version")?.let { "modRuntimeOnly"("maven.modrinth:journeymap:$it") }

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.slf4j:slf4j-api:2.0.12")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.12")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<JavaExec>().matching { it.name == "runClient" }.configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
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
        // Minecraft の対応範囲（Maven 形式。versions/<構成>/gradle.properties に置く）。
        // この構成向けの jar が別のマイナー版で読み込まれないよう、上限を持つ範囲を入れる
        "mc_version_range" to project.property("mc_version_range").toString(),
        // Forge 自体の範囲は下限だけにする。上限を入れると、将来のローダー更新で読み込めなくなる
        "loader" to project.property("forge_version").toString()
    )
    props.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("META-INF/*.toml") {
        expand(props)
    }
}
