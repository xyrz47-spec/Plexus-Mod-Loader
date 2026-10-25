plugins {
    id("poly.java-conventions")
}

description = "Poly Mod Loader runtime: discovery, metadata validation, dependency resolution, game class loading, lifecycle."

dependencies {
    api(project(":poly-lifecycle-api"))
    api(project(":poly-version-adapter-api"))
    implementation(project(":poly-mod-metadata"))
    implementation(project(":poly-transform-engine"))
    implementation(project(":poly-system-registry"))
    // LaunchConfiguration carries the bridge SPI's SystemClassPath (ADR-0024).
    api(project(":poly-bridge-api"))
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
