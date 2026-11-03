rootProject.name = "lithium"

pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        gradlePluginPortal()
    }
}
includeBuild("components/mixin-config-plugin")

include("common")
//Comment out fabric or neoforge to disable the respective platform
include("fabric")
include("neoforge")
include("forge")
