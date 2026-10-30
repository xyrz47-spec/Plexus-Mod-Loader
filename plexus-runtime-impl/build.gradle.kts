plugins {
    id("poly.java-conventions")
}

description = "Poly Mod Loader runtime: discovery, metadata validation, dependency resolution, game class loading, lifecycle."

dependencies {
    api(project(":plexus-lifecycle-api"))
    api(project(":plexus-version-adapter-api"))
    implementation(project(":plexus-mod-metadata"))
    implementation(project(":plexus-transform-engine"))
    implementation(project(":plexus-system-registry"))
    // The early loading window, shown in its own process while the game starts.
    implementation(project(":plexus-early-window"))
    // LaunchConfiguration carries the bridge SPI's SystemClassPath (ADR-0024).
    api(project(":plexus-bridge-api"))
    implementation(libs.slf4j.api)
    implementation(libs.gson)

    testImplementation(libs.asm)
}

tasks.processResources {
    val runtimeVersion = project.version.toString()
    inputs.property("runtimeVersion", runtimeVersion)
    filesMatching("dev/polymodloader/runtime/poly-runtime.properties") {
        expand("runtimeVersion" to runtimeVersion)
    }
}
