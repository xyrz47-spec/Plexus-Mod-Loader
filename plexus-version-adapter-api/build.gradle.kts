plugins {
    id("plexus.java-conventions")
}

description = "Contract every Minecraft version adapter implements."

dependencies {
    api(project(":plexus-runtime-api"))
    api(project(":plexus-transform-api"))
    api(project(":plexus-system-api"))
    api(project(":plexus-event-api"))
    api(project(":plexus-game-api"))
    // ModCountLinesTest reads the bundled language files.
    testImplementation(libs.gson)
}
