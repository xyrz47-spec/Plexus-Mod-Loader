pluginManagement {
    // -PplexusLocalSdk: use a local Plexus Mod Loader build (./gradlew publishToMavenLocal and
    // ./gradlew -p build-logic publishToMavenLocal in Plexus Mod Loader) even for a version that is published.
    val localFirst = providers.gradleProperty("plexusLocalSdk").isPresent
    repositories {
        if (localFirst) mavenLocal()
        // The Plexus Mod Loader SDK: the dev.plexusmodloader.mod plugin and, through it, Plexus Mod Loader itself.
        maven("https://www.plexusmc.net/maven/") {
            name = "Plexus Mod Loader"
            content { includeGroupByRegex("dev\\.(plexus|poly)modloader(\\..*)?") }
        }
        // A local Plexus Mod Loader build of a version the SDK repository does not have.
        if (!localFirst) mavenLocal()
        gradlePluginPortal()
    }
}

rootProject.name = "my-plexus-mod"
