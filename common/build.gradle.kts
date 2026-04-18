
plugins {
    id("java")
    id("idea")
    id("net.fabricmc.fabric-loom") version ("1.15-SNAPSHOT")
}

repositories {
    maven("https://maven.parchmentmc.org/")
}


val MINECRAFT_COMPILE_VERSION: String by rootProject.extra
val PARCHMENT_VERSION: String? by rootProject.extra
val FABRIC_LOADER_VERSION: String by rootProject.extra
val FABRIC_API_VERSION: String by rootProject.extra

dependencies {
    minecraft(group = "com.mojang", name = "minecraft", version = MINECRAFT_COMPILE_VERSION)
    compileOnly("net.fabricmc:fabric-loader:$FABRIC_LOADER_VERSION")

    fun addDependentFabricModule(name: String) {
        val module = fabricApi.module(name, FABRIC_API_VERSION)
        compileOnly(module)
    }

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

    //Copied from fabric build.gradle to avoid having multiple slightly different (few fabric interfaces added) minecraft merged mapped jars
    addCompileOnlyFabricModule("fabric-transfer-api-v1")
    addFabricModule("fabric-gametest-api-v1")
    addFabricModule("fabric-registry-sync-v0")


    // example usage:
    //    addDependentFabricModule("fabric-block-view-api-v2")

    compileOnly("net.caffeinemc:mixin-config-plugin:1.0-SNAPSHOT")
}

sourceSets {
    val main = getByName("main")
    val api = create("api")
    val gametest = create("gametest")

    api.apply {
        java {
            compileClasspath += main.compileClasspath
        }
    }

    main.apply {
        java {
            compileClasspath += api.output
            runtimeClasspath += api.output
        }
    }

    gametest.apply {
        java.srcDir("src/gametest/java")
        resources.srcDir("src/gametest/resources")
    }
}


tasks.named<Copy>("processGametestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Jar>("apiJar") {
    from(sourceSets["api"].output)
    archiveBaseName.set("lithium")
    archiveClassifier.set("api")
    destinationDirectory = rootDir.resolve("build").resolve("libs")
}

tasks.named<Jar>("jar") {
    from(sourceSets["api"].output.classesDirs)
    from(sourceSets["api"].output.resourcesDir)
}

tasks.named("build") {
    dependsOn("apiJar")
}

loom {
    mixin {
        useLegacyMixinAp = false
    }

    accessWidenerPath = file("src/main/resources/lithium.accesswidener")

    mods {
        val main by creating { // to match the default mod generated for Forge
            sourceSet("api")
            sourceSet("main")
        }
    }
}

tasks {
    jar {
        from(rootDir.resolve("LICENSE.md"))

        val api = sourceSets.getByName("api")
        from(api.output.classesDirs)
        from(api.output.resourcesDir)
    }
}

// This trick hides common tasks in the IDEA list.
tasks.configureEach {
    group = null
}