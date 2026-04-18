import org.gradle.internal.extensions.core.extra

plugins {
    id("java")
    id("net.fabricmc.fabric-loom") version ("1.15-SNAPSHOT") apply (false)
    id("me.modmuss50.mod-publish-plugin") version ("0.8.1") apply (false)

}

// Fabric: https://fabricmc.net/develop/
// Neoforge: https://neoforged.net/
val MINECRAFT_COMPILE_VERSION by extra { "26.1.2" }

val MC_DISPLAY_VERSION by extra { "26.1.x" } //Used for human read text
val MC_SUPPORTED_RANGE_FABRIC by extra { "~26.1" } // e.g. "~26.1", format: https://docs.npmjs.com/about-semantic-versioning
val MC_SUPPORTED_RANGE_NEOFORGE by extra { "[26.1, 26.2)" } // e.g. "[26.1, 26.2)", format: https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
val MC_PUBLISHING_MIN_VERSION by extra { "26.1" } // Minimum mc version for mod publish plugin, format: https://modmuss50.github.io/mod-publish-plugin/platforms/modrinth/
val MC_PUBLISHING_MAX_VERSION by extra { "26.1.2" } //Inclusive maximum mc version for mod publish plugin

val NEOFORGE_VERSION by extra { "26.1.2.2-beta" }
val FABRIC_LOADER_VERSION by extra { "0.18.6" }
val FABRIC_API_VERSION by extra { "0.145.4+26.1.2" }

// https://semver.org/
val MOD_VERSION by extra { "0.24.1" }

// This value can be set to null to disable Parchment.
val PARCHMENT_VERSION by extra { null }


allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    enabled = false
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "me.modmuss50.mod-publish-plugin")

    java.toolchain.languageVersion = JavaLanguageVersion.of(25)


    fun createVersionString(): String {
        val builder = StringBuilder()

        val isReleaseBuild = providers.environmentVariable("RELEASE_WORKFLOW").isPresent
        val buildId = System.getenv("GITHUB_RUN_NUMBER")

        if (isReleaseBuild) {
            builder.append(MOD_VERSION)
        } else {
            builder.append(MOD_VERSION.substringBefore('-'))
            builder.append("-snapshot")
        }

        builder.append("+mc").append(MINECRAFT_COMPILE_VERSION)

        if (!isReleaseBuild) {
            if (buildId != null) {
                builder.append("-build.${buildId}")
            } else {
                builder.append("-local")
            }
        }

        return builder.toString()
    }

    version = createVersionString()
    group = "net.caffeinemc.mods"

    java.toolchain.languageVersion = JavaLanguageVersion.of(25)

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }

    //make builds more reproducible
    tasks.withType<AbstractArchiveTask>().configureEach {
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
    }
}

tasks.register("lithiumPublish") {
    when (val platform = providers.environmentVariable("PLATFORM").orNull) {
        "both" -> {
            dependsOn(tasks.build, ":fabric:publishMods", ":neoforge:publishMods")
        }
        "fabric", "forge" -> {
            dependsOn("${platform}:build", "${platform}:publish", "${platform}:publishMods")
        }
        else -> {
            val isRelease = providers.environmentVariable("RELEASE_WORKFLOW").orNull;
            if (isRelease != null && isRelease == "true")
                throw IllegalStateException("Environment variable PLATFORM cannot be null when running on CI!")
        }
    }
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(MINECRAFT_COMPILE_VERSION)
    }
}

tasks.register("printMinecraftDisplayVersion") {
    doLast {
        println(MC_DISPLAY_VERSION)
    }
}

tasks.register("printModVersion") {
    doLast {
        println(MOD_VERSION)
    }
}
