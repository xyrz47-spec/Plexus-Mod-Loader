pluginManagement {
    // -PpolyLocalSdk: use a local Poly Mod Loader build (./gradlew publishToMavenLocal and
    // ./gradlew -p build-logic publishToMavenLocal in Poly Mod Loader) even for a version that is published.
    val localFirst = providers.gradleProperty("polyLocalSdk").isPresent
    repositories {
        if (localFirst) mavenLocal()
        // The Poly Mod Loader SDK: the dev.polymodloader.mod plugin and, through it, Poly Mod Loader itself.
        maven("https://www.polymc.net/maven/") {
            name = "Poly Mod Loader"
            content { includeGroupByRegex("dev\\.polymodloader(\\..*)?") }
        }
        // A local Poly Mod Loader build of a version the SDK repository does not have.
        if (!localFirst) mavenLocal()
        gradlePluginPortal()
    }
}

rootProject.name = "crystal-garden"
