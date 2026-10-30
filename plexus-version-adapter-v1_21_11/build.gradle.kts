plugins {
    id("poly.java-conventions")
}

description = "Version adapter for Minecraft 1.21.11: launch targets and built-in transformation rules."

dependencies {
    implementation(project(":plexus-version-adapter-api"))
    implementation(libs.slf4j.api)
    // The game layer is found on Poly Mod Loader's class path at launch (ADR-0010); publishing it as a runtime
    // dependency puts it on every Poly Mod Loader class path that includes this adapter.
    runtimeOnly(project(":plexus-game-v1_21_11"))
}
