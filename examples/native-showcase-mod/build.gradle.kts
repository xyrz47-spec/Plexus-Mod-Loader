plugins {
    id("poly.java-conventions")
}

description = "Native API showcase: a block, items, data and assets, a command, a network channel, events, client key binding and HUD layer, and in-game tests."

dependencies {
    compileOnly(project(":poly-lifecycle-api"))
    compileOnly(libs.slf4j.api)
}
