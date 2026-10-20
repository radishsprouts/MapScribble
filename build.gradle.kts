plugins {
    // Applies the correct loom variant for the Minecraft version (unobfuscated loom on 26.1+)
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava = JavaVersion.VERSION_25

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://maven.blamejared.com", "BlameJared", "info.journeymap")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

fabricApi {
    // src/gametest: client gametests that start a real client (run with `runClientGameTest`)
    configureTests {
        createSourceSet = true
        modId = "mapscribble-test"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

dependencies {
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, sc.properties["deps.fabric_api"]))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    // `mod*` configurations are converted to plain ones by loom-back-compat on 26.1+
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    fapi(
        "fabric-api-base",
        "fabric-lifecycle-events-v1",
        "fabric-screen-api-v1",
        "fabric-key-mapping-api-v1",
        "fabric-networking-api-v1",
    )

    // Client gametests run with the full Fabric API
    "gametestImplementation"("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // JourneyMap API: compile time only. Never bundled in the jar (JourneyMap API license)
    compileOnly("info.journeymap:journeymap-api-fabric:${sc.properties.get<String>("deps.jm_api")}") { isTransitive = false }
    // JourneyMap itself: only for the dev client and the client gametests
    modLocalRuntime("maven.modrinth:journeymap:${sc.properties.get<String>("deps.jm")}")
    "gametestRuntimeOnly"("maven.modrinth:journeymap:${sc.properties.get<String>("deps.jm")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    withType<Test> { useJUnitPlatform() }

    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("version", project.version.toString())
            register("minecraft", sc.properties["mod.mc_compat"])
            register("loader", Regex("\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value)
        }

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    withType<Jar> {
        val name = project.property("mod.id")
        inputs.property("mod_id", name)
        from("../../LICENSE") { rename { "${it}_$name" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies them to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}
