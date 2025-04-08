import me.modmuss50.mpp.ReleaseType

plugins {
    id("idea")
    id("net.neoforged.moddev") version "2.0.42-beta"
    id("java-library")
    id("net.caffeinemc.mixin-config-plugin") version ("1.0-SNAPSHOT")
}

val MINECRAFT_VERSION: String by rootProject.extra
val PARCHMENT_VERSION: String? by rootProject.extra
val NEOFORGE_VERSION: String by rootProject.extra
val MOD_VERSION: String by rootProject.extra

base {
    archivesName = "lithium-neoforge"
}

project.sourceSets {
    main.get().apply {
    }

    val main by getting

    create("gametest") {
        java.srcDirs("src/gametest/java")
        resources.srcDirs("src/gametest/resources")

        compileClasspath += main.compileClasspath
        runtimeClasspath += main.runtimeClasspath
        compileClasspath += main.output
        runtimeClasspath += main.output
    }
}

repositories {
    maven("https://maven.pkg.github.com/ims212/Forge_Fabric_API") {
        credentials {
            username = "IMS212"
            // Read only token
            password = "ghp_" + "DEuGv0Z56vnSOYKLCXdsS9svK4nb9K39C1Hn"
        }
    }
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

tasks.jar {
    val api = project.project(":common").sourceSets.getByName("api")
    from(api.output.classesDirs)
    from(api.output.resourcesDir)

    val main = project.project(":common").sourceSets.getByName("main")
    from(main.output.classesDirs) {
        exclude("/lithium.refmap.json")
    }
    from(main.output.resourcesDir)

    from(rootDir.resolve("LICENSE.md"))

    filesMatching("neoforge.mods.toml") {
        expand(mapOf("version" to MOD_VERSION))
    }
}

tasks.jar.get().destinationDirectory = rootDir.resolve("build").resolve("libs")

neoForge {
    // Specify the version of NeoForge to use.
    version = NEOFORGE_VERSION

    if (PARCHMENT_VERSION != null) {
        parchment {
            minecraftVersion = MINECRAFT_VERSION
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
        create("gametestClient") {
            client()
            gameDirectory.set(file("runs/gametestClient"))

            sourceSet = sourceSets.getByName("gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "lithium-gametest")
            environment("LITHIUM_GAMETEST_RESOURCES", file(gameDirectory.get().asFile).resolve("../../../common/src/gametest/resources/data/lithium-gametest/gametest/structure").path)
        }
        create("gametestServer") {
            type = "gameTestServer"
            gameDirectory.set(file("runs/gametestServer"))

            sourceSet = sourceSets.getByName("gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", "lithium-gametest")
            environment("LITHIUM_GAMETEST_RESOURCES", file(gameDirectory.get().asFile).resolve("../../../common/src/gametest/resources/data/lithium-gametest/gametest/structure").path)
        }
    }

    mods {
        create("lithium") {
            sourceSet(project.sourceSets.main.get())
            sourceSet(project.project(":common").sourceSets.main.get())
            sourceSet(project.project(":common").sourceSets.getByName("api"))
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
    compileOnly(project.project(":common").sourceSets.getByName("main").output)
    compileOnly(project.project(":common").sourceSets.getByName("api").output)

    compileOnly("net.caffeinemc:mixin-config-plugin:1.0-SNAPSHOT")
    //In case of fabric-api dependencies, consider using forgified-fabric-api:
//    includeDep("org.sinytra.forgified-fabric-api:fabric-block-view-api-v2:1.0.10+9afaaf8c19")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

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
                    project(":common").tasks.named("compileJava", JavaCompile::class).get().destinationDirectory.get(),
            )
    )
    includeFiles.set(file("src/main/java/net/caffeinemc/mods/lithium"))
    outputDirectory.set(layout.buildDirectory.dir("neoforge-mixin-config-output"))
    outputAssetsPath = "assets/lithium"
    outputFilenameForSummaryDocument = "lithium-neoforge-mixin-config.md"
    mixinParentPackages = listOf("net.caffeinemc.mods.lithium", "net.caffeinemc.mods.lithium.neoforge")
    modShortName = "Lithium"

    dependsOn("compileJava")
    dependsOn(project(":common").tasks.named("compileJava", JavaCompile::class))

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
    val mcVersionLithiumVersion = "mc$MINECRAFT_VERSION-$MOD_VERSION"
    version = "$mcVersionLithiumVersion-neoforge"
    file = tasks.jar.get().archiveFile
    changelog = rootProject.file("CHANGELOG.md").readText().trim()
    type = getReleaseType()
    modLoaders.add("neoforge")

    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_API_KEY")
        projectId = "360438"
        minecraftVersions.add(MINECRAFT_VERSION)
        displayName = "Lithium $mcVersionLithiumVersion for Neoforge"
    }

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_API_KEY")
        projectId = "gvQqBUqZ"
        minecraftVersions.add(MINECRAFT_VERSION)
        displayName = "Lithium $MOD_VERSION for Neoforge"
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