import me.modmuss50.mpp.ReleaseType

plugins {
    id("idea")
    id("net.neoforged.moddev") version "2.0.147"
    id("java")
    id("net.caffeinemc.mixin-config-plugin") version ("1.0-SNAPSHOT")
}

val MINECRAFT_COMPILE_VERSION: String by rootProject.extra
val MC_DISPLAY_VERSION: String by rootProject.extra
val MC_SUPPORTED_RANGE_NEOFORGE: String by rootProject.extra
val MC_PUBLISHING_MIN_VERSION: String by rootProject.extra
val MC_PUBLISHING_MAX_VERSION: String by rootProject.extra
val NEOFORGE_VERSION: String by rootProject.extra
val PARCHMENT_VERSION: String? by rootProject.extra
val MOD_VERSION: String by rootProject.extra

base {
    archivesName = "lithium-neoforge"
}

project.sourceSets {
    main.get().apply {
    }

    val main by getting
    val parent = project(":common").sourceSets.getByName("gametest")

    main {
        java.srcDirs(
                project(":common").sourceSets.getByName("api").java.srcDirs,
                project(":common").sourceSets.getByName("main").java.srcDirs
        )
        resources.srcDirs(project(":common").sourceSets.getByName("main").resources.srcDirs)
    }

    create("gametest") {
        java.srcDirs("src/gametest/java")
        resources.srcDirs("src/gametest/resources")

        compileClasspath += main.compileClasspath
        runtimeClasspath += main.runtimeClasspath
        compileClasspath += main.output
        runtimeClasspath += main.output
        compileClasspath += parent.compileClasspath
        runtimeClasspath += parent.runtimeClasspath
        java.srcDirs(parent.java.srcDirs)
    }
}

tasks.named<Copy>("processGametestResources") {
    from(project(":common").sourceSets.getByName("gametest").resources.srcDirs)
    into("build/resources/gametest")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

repositories {
    maven("https://maven.su5ed.dev/releases")
    maven("https://maven.neoforged.net/releases/")

    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

tasks.processResources {
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(mapOf(
                "version" to MOD_VERSION,
                "mc_version_dependency" to MC_SUPPORTED_RANGE_NEOFORGE
        ))
    }
}

tasks.jar {
    from(rootDir.resolve("LICENSE.md"))
}

tasks.jar.get().destinationDirectory = rootDir.resolve("build").resolve("libs")

neoForge {
    // Specify the version of NeoForge to use.
    version = NEOFORGE_VERSION

    if (PARCHMENT_VERSION != null) {
        parchment {
            minecraftVersion = MINECRAFT_COMPILE_VERSION
            mappingsVersion = PARCHMENT_VERSION
        }
    }

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
        }
        create("clientGameTest") {
            client()
            gameDirectory.set(file("run/gametestClient"))

            sourceSet = sourceSets.getByName("gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "lithium-gametest")
            environment("LITHIUM_GAMETEST_RESOURCES", project.parent!!.findProject(":common")!!.projectDir.absolutePath + "/src/gametest/resources")
        }
        create("gameTest") {
            type = "gameTestServer"
            gameDirectory.set(file("run/gametestServer"))

            sourceSet = sourceSets.getByName("gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "lithium-gametest")
            environment("LITHIUM_GAMETEST_RESOURCES", project.parent!!.findProject(":common")!!.projectDir.absolutePath + "/src/gametest/resources")
        }
    }

    mods {
        create("lithium") {
            sourceSet(project.sourceSets.main.get())
            sourceSet(project.sourceSets.getByName("gametest"))
        }
    }
}

fun includeDep(dependency: String, closure: Action<ExternalModuleDependency>) {
    dependencies.implementation(dependency, closure)
    dependencies.jarJar(dependency, closure)
}

fun includeDep(dependency: String) {
    dependencies.implementation(dependency)
    dependencies.jarJar(dependency)
}

tasks.named("compileTestJava").configure {
    enabled = false
}

dependencies {
    compileOnly("net.caffeinemc:mixin-config-plugin:1.0-SNAPSHOT")
    //In case of fabric-api dependencies, consider using forgified-fabric-api:
//    includeDep("org.sinytra.forgified-fabric-api:fabric-block-view-api-v2:1.0.10+9afaaf8c19")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

project.sourceSets {
    val main by getting {
        resources {
            srcDir(layout.buildDirectory.dir("neoforge-mixin-config-output"))
        }
    }
}

tasks.named<net.caffeinemc.gradle.CreateMixinConfigTask>("neoforgeCreateMixinConfig") {
    inputFiles.set(
            listOf(
                    tasks.named("compileJava", JavaCompile::class).get().destinationDirectory.get(),
            )
    )
    includeFiles.set(file("src/main/java/net/caffeinemc/mods/lithium"))
    outputDirectory.set(layout.buildDirectory.dir("neoforge-mixin-config-output"))
    outputAssetsPath = "assets/lithium"
    outputFilenameForSummaryDocument = "lithium-neoforge-mixin-config.md"
    mixinParentPackages = listOf("net.caffeinemc.mods.lithium", "net.caffeinemc.mods.lithium.neoforge")
    modShortName = "Lithium"

    dependsOn("compileJava")

    doLast {
        copy {
            from(layout.buildDirectory.dir("neoforge-mixin-config-output").get().file("lithium-neoforge-mixin-config.md"))
            into(rootDir)
        }
    }
}

tasks.named("processResources") {
    dependsOn("neoforgeCreateMixinConfig")
}

publishMods {
    val mcDisplayVersionLithiumVersion = "mc$MC_DISPLAY_VERSION-$MOD_VERSION"
    val mcCompileVersionLithiumVersion = "mc$MINECRAFT_COMPILE_VERSION-$MOD_VERSION"
    val publishSite = providers.environmentVariable("PUBLISH_SITE").orNull ?: "both"
    val publishToCurseforge = publishSite == "both" || publishSite == "curseforge"
    val publishToModrinth = publishSite == "both" || publishSite == "modrinth"

    if (!publishToCurseforge && !publishToModrinth) {
        throw IllegalArgumentException("PUBLISH_SITE must be modrinth, curseforge or both!")
    }

    version = "$mcCompileVersionLithiumVersion-neoforge"
    file = tasks.jar.get().archiveFile
    changelog = rootProject.file("CHANGELOG.md").readText().trim()
    type = getReleaseType()
    modLoaders.add("neoforge")

    if (publishToCurseforge) {
        curseforge {
            accessToken = providers.environmentVariable("CURSEFORGE_API_KEY")
            projectId = "360438"
            minecraftVersionRange {
                start = "$MC_PUBLISHING_MIN_VERSION"
                end = "$MC_PUBLISHING_MAX_VERSION"
            }
            displayName = "Lithium $mcDisplayVersionLithiumVersion for Neoforge"
            client = true
            server = true
        }
    }

    if (publishToModrinth) {
        modrinth {
            accessToken = providers.environmentVariable("MODRINTH_API_KEY")
            projectId = "gvQqBUqZ"
            minecraftVersionRange {
                start = "$MC_PUBLISHING_MIN_VERSION"
                end = "$MC_PUBLISHING_MAX_VERSION"
            }
            displayName = "Lithium $MOD_VERSION for Neoforge"
        }
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