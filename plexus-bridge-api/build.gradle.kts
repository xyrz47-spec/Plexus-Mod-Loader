plugins {
    id("plexus.java-conventions")
}

description = "Service interface for loader compatibility bridges and the shared compatibility vocabulary."

dependencies {
    api(project(":plexus-runtime-api"))
    // A host loader declares Plexus Mod Loader's rules for its patched game (GameHost.transformRules).
    api(project(":plexus-transform-api"))
}
