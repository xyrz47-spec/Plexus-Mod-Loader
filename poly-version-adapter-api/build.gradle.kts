plugins {
    id("poly.java-conventions")
}

description = "Contract every Minecraft version adapter implements."

dependencies {
    api(project(":poly-runtime-api"))
    api(project(":poly-transform-api"))
    api(project(":poly-system-api"))
    api(project(":poly-event-api"))
    api(project(":poly-game-api"))
    // ModCountLinesTest reads the bundled language files.
    testImplementation(libs.gson)
}
