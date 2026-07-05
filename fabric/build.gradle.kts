import me.modmuss50.mpp.ReleaseType

plugins {
    id("java")
    id("idea")
    id("net.fabricmc.fabric-loom") version ("1.15-SNAPSHOT")
    id("net.caffeinemc.mixin-config-plugin") version ("1.0-SNAPSHOT")
}

val MINECRAFT_COMPILE_VERSION: String by rootProject.extra
val MC_DISPLAY_VERSION: String by rootProject.extra
val MC_SUPPORTED_RANGE_FABRIC: String by rootProject.extra
val MC_PUBLISHING_MIN_VERSION: String by rootProject.extra
val MC_PUBLISHING_MAX_VERSION: String by rootProject.extra
val FABRIC_LOADER_VERSION: String by rootProject.extra
val FABRIC_API_VERSION: String by rootProject.extra
val PARCHMENT_VERSION: String? by rootProject.extra
val MOD_VERSION: String by rootProject.extra

base {
    archivesName.set("lithium-fabric")
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "lithium-gametest"
        enableGameTests = true
        enableClientGameTests = true
        eula = true // By setting this to true, you agree to the Minecraft EULA.
        clearRunDirectory = false
    }
}

// Remove the gametest from test (unit tests only)
afterEvaluate {
    tasks.named("test") {
        setDependsOn(dependsOn.filterNot {
            it.toString().contains("runGameTest")
        })
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${MINECRAFT_COMPILE_VERSION}")
    implementation("net.fabricmc:fabric-loader:$FABRIC_LOADER_VERSION")

    fun addEmbeddedFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        implementation(module)
        include(module)
    }

    fun addCompileOnlyFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        compileOnly(module)
    }

    fun addFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        implementation(module)
    }

    addCompileOnlyFabricModule("fabric-transfer-api-v1")
    addFabricModule("fabric-gametest-api-v1")
    addFabricModule("fabric-registry-sync-v0")



    implementation("com.google.code.findbugs:jsr305:3.0.1")

    implementation(project.project(":common").sourceSets.getByName("api").output)
    implementation(project.project(":common").sourceSets.getByName("main").output)

    compileOnly("net.caffeinemc:mixin-config-plugin:1.0-SNAPSHOT")

    testImplementation("net.fabricmc:fabric-loader-junit:${FABRIC_LOADER_VERSION}")
}

tasks.test {
    useJUnitPlatform()

    // Disable caching to ensure tests always run
    outputs.upToDateWhen { false }
}

// Run tests with mixins disabled to ensure vanilla actually passes our tests
tasks.register<Test>("testVanilla") {
    useJUnitPlatform()

    // Disable caching to ensure tests always run
    outputs.upToDateWhen { false }

    jvmArgs("-Dlithium.test.disable_all_mixins=true")

    description = "Runs tests with lithium mixins disabled."
    group = "verification"

    // Use the same compiled test classes and classpath as the normal test task
    val testTask = tasks.named<Test>("test").get()
    testClassesDirs = testTask.testClassesDirs
    classpath = testTask.classpath
}

//Mixin hotswap, debug flags
afterEvaluate {
    loom.runs.configureEach {
        // https://fabricmc.net/wiki/tutorial:mixin_hotswaps
        vmArg("-javaagent:${ configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") } }")
        vmArg("-XX:+UnlockDiagnosticVMOptions")
        vmArg("-XX:+DebugNonSafepoints")
        vmArg("-Dmixin.debug.export=true")
        vmArg("-Dmixin.debug=true")
    }
}

sourceSets {
    val main by getting
    val parent = project(":common").sourceSets.getByName("gametest")

    val gametest by getting {
        java.srcDir("src/gametest/java")
        resources.srcDir("src/gametest/resources")

        compileClasspath += main.compileClasspath
        runtimeClasspath += main.runtimeClasspath
        compileClasspath += main.output
        runtimeClasspath += main.output
        compileClasspath += parent.compileClasspath
        runtimeClasspath += parent.runtimeClasspath
    }

    test {
        java.srcDir("src/test/java")
        resources.srcDir("src/test/resources")
    }
}

tasks.named<Copy>("processGametestResources") {
    from(project(":common").sourceSets.getByName("gametest").resources.srcDirs)
    into("build/resources/gametest")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Copy>("processTestResources") {
    from(project(":common").sourceSets.getByName("test").resources.srcDirs)
    into("build/resources/test")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

loom {
    if (project(":common").file("src/main/resources/lithium.accesswidener").exists())
        accessWidenerPath.set(project(":common").file("src/main/resources/lithium.accesswidener"))

    mixin {
        useLegacyMixinAp = false
    }

    runs {
        register("fabricClient") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("run")
        }
        register("fabricServer") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("run")
        }

        val gameTest by getting {
            server()
            name = "Game Test Server"
            vmArg("-Dfabric-api.gametest")
            runDir = "run/gametestServer"
            source(sourceSets["gametest"])
            environmentVariable("LITHIUM_GAMETEST_RESOURCES", project(":common").file("src/gametest/resources").path)
        }
        val clientGameTest by getting {
            client()
            name = "Game Test Client"
            vmArg("-Dfabric-api.gametest")
            runDir = "run/gametestClient"
            source(sourceSets["gametest"])
            environmentVariable("LITHIUM_GAMETEST_RESOURCES", project(":common").file("src/gametest/resources").path)
        }
    }
}

tasks {
    processResources {
        from(project.project(":common").sourceSets.main.get().resources)
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mapOf(
                    "version" to project.version,
                    "mc_version_dependency" to MC_SUPPORTED_RANGE_FABRIC
            ))
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        from(zipTree(project.project(":common").tasks.jar.get().archiveFile))
        destinationDirectory = rootDir.resolve("build").resolve("libs")
    }
}

sourceSets {
    main {
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
    val mcDisplayVersionLithiumVersion = "mc$MC_DISPLAY_VERSION-$MOD_VERSION"
    val mcCompileVersionLithiumVersion = "mc$MINECRAFT_COMPILE_VERSION-$MOD_VERSION"
    version = "$mcCompileVersionLithiumVersion-fabric"
    file = tasks.jar.get().archiveFile
    changelog = rootProject.file("CHANGELOG.md").readText().trim()
    type = getReleaseType()
    modLoaders.add("fabric")
    modLoaders.add("quilt")

    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_API_KEY")
        projectId = "360438"
        minecraftVersionRange {
            start = "$MC_PUBLISHING_MIN_VERSION"
            end = "$MC_PUBLISHING_MAX_VERSION"
        }
        displayName = "Lithium $mcDisplayVersionLithiumVersion for Fabric"
    }

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_API_KEY")
        projectId = "gvQqBUqZ"
        minecraftVersionRange {
            start = "$MC_PUBLISHING_MIN_VERSION"
            end = "$MC_PUBLISHING_MAX_VERSION"
        }
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