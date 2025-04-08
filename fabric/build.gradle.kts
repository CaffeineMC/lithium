import me.modmuss50.mpp.ReleaseType

plugins {
    id("java")
    id("idea")
    id("fabric-loom") version ("1.8.9")
    id("net.caffeinemc.mixin-config-plugin") version ("1.0-SNAPSHOT")
}

val MINECRAFT_VERSION: String by rootProject.extra
val PARCHMENT_VERSION: String? by rootProject.extra
val FABRIC_LOADER_VERSION: String by rootProject.extra
val FABRIC_API_VERSION: String by rootProject.extra
val MOD_VERSION: String by rootProject.extra

base {
    archivesName.set("lithium-fabric")
}

dependencies {
    minecraft("com.mojang:minecraft:${MINECRAFT_VERSION}")
    mappings(loom.layered {
        officialMojangMappings()
        if (PARCHMENT_VERSION != null) {
            parchment("org.parchmentmc.data:parchment-${MINECRAFT_VERSION}:${PARCHMENT_VERSION}@zip")
        }
    })
    modImplementation("net.fabricmc:fabric-loader:$FABRIC_LOADER_VERSION")

    fun addEmbeddedFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        modImplementation(module)
        include(module)
    }

    fun addCompileOnlyFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        modCompileOnly(module)
    }

    fun addFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        modImplementation(module)
    }

    addCompileOnlyFabricModule("fabric-transfer-api-v1")
    addFabricModule("fabric-gametest-api-v1")



    implementation("com.google.code.findbugs:jsr305:3.0.1")

    implementation(project.project(":common").sourceSets.getByName("api").output)
    implementation(project.project(":common").sourceSets.getByName("main").output)

    compileOnly("net.caffeinemc:mixin-config-plugin:1.0-SNAPSHOT")
}

tasks.named("compileTestJava").configure {
    enabled = false
}

tasks.named("test").configure {
    enabled = false
}

//Mixin hotswap
afterEvaluate {
    loom.runs.configureEach {
        // https://fabricmc.net/wiki/tutorial:mixin_hotswaps
        vmArg("-javaagent:${ configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") } }")
    }
}

sourceSets {
    val main by getting
    val parent = project(":common").sourceSets.getByName("gametest")

    create("gametest") {
        java.srcDir("src/gametest/java")
        resources.srcDir("src/gametest/resources")

        compileClasspath += main.compileClasspath
        runtimeClasspath += main.runtimeClasspath
        compileClasspath += main.output
        runtimeClasspath += main.output
        compileClasspath += parent.compileClasspath
        runtimeClasspath += parent.runtimeClasspath
    }
}

tasks.named<Copy>("processGametestResources") {
    from(project(":common").sourceSets.getByName("gametest").resources.srcDirs)
    into("build/resources/gametest")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

loom {
    if (project(":common").file("src/main/resources/lithium.accesswidener").exists())
        accessWidenerPath.set(project(":common").file("src/main/resources/lithium.accesswidener"))

    mixin {
        useLegacyMixinAp = false
    }

    runs {
        create("fabricClient") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
        }
        create("fabricServer") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("run")
        }

        create("gametestServer") {
            server()
            name = "Game Test"
            vmArg("-Dfabric-api.gametest")
            runDir = "build/gametest"
            source(sourceSets["gametest"])
        }
        create("gametestClient") {
            client()
            name = "Game Test Client"
            vmArg("-Dfabric-api.gametest")
            runDir = "build/gametest"
            source(sourceSets["gametest"])
        }
    }

    mods {
        create("lithium-gametest") {
            sourceSet(sourceSets.getByName("gametest"))
        }
    }
}

tasks {
    processResources {
        from(project.project(":common").sourceSets.main.get().resources)
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(zipTree(project.project(":common").tasks.jar.get().archiveFile))
    }

    remapJar.get().destinationDirectory = rootDir.resolve("build").resolve("libs")
}

sourceSets {
    val main by getting {
        resources {
            srcDir(layout.buildDirectory.dir("fabric-mixin-config-output"))
        }
    }
}

tasks.named<net.caffeinemc.gradle.CreateMixinConfigTask>("fabricCreateMixinConfig") {
    inputFiles.set(
            listOf(
                    tasks.named("compileJava", JavaCompile::class).get().destinationDirectory.get(),
                    project(":common").tasks.named("compileJava", JavaCompile::class).get().destinationDirectory.get(),
            )
    )
    includeFiles.set(file("src/main/java/net/caffeinemc/mods/lithium"))
    outputDirectory.set(layout.buildDirectory.dir("fabric-mixin-config-output"))
    outputAssetsPath = "assets/lithium"
    outputFilenameForSummaryDocument = "lithium-fabric-mixin-config.md"
    mixinParentPackages = listOf("net.caffeinemc.mods.lithium", "net.caffeinemc.mods.lithium.fabric")
    modShortName = "Lithium"

    dependsOn("compileJava")
    dependsOn(project(":common").tasks.named("compileJava", JavaCompile::class))

    doLast {
        copy {
            from(layout.buildDirectory.dir("fabric-mixin-config-output").get().file("lithium-fabric-mixin-config.md"))
            into(rootDir)
        }
    }
}

tasks.named("processResources") {
    dependsOn("fabricCreateMixinConfig")
}

publishMods {
    val mcVersionLithiumVersion = "mc$MINECRAFT_VERSION-$MOD_VERSION"
    version = "$mcVersionLithiumVersion-fabric"
    file = tasks.remapJar.get().archiveFile
    changelog = rootProject.file("CHANGELOG.md").readText().trim()
    type = getReleaseType()
    modLoaders.add("fabric")
    modLoaders.add("quilt")

    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_API_KEY")
        projectId = "360438"
        minecraftVersions.add(MINECRAFT_VERSION)
        displayName = "Lithium $mcVersionLithiumVersion for Fabric"
    }

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_API_KEY")
        projectId = "gvQqBUqZ"
        minecraftVersions.add(MINECRAFT_VERSION)
        displayName = "Lithium $MOD_VERSION for Fabric"
    }
}

fun getReleaseType(): ReleaseType {
    return when (val releaseType = providers.environmentVariable("RELEASE_TYPE").orNull) {
        "alpha"-> ReleaseType.ALPHA
        "beta" -> ReleaseType.BETA
        "stable" -> ReleaseType.STABLE
        else -> {
            if (releaseType != null)
                throw IllegalArgumentException("Release type must be alpha, beta or stable!")

            ReleaseType.STABLE
        }
    }
}