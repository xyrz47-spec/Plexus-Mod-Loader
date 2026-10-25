plugins {
    id("poly.java-conventions")
}

description = "Poly Mod that requires a Fabric library."

dependencies {
    compileOnly(project(":poly-lifecycle-api"))
    compileOnly(libs.slf4j.api)
    compileOnly(project(":test-mods:fabric-library"))
    // FabricLib implements a Fabric interface, so compiling against it needs that interface too.
    compileOnly(libs.fabric.loader) { isTransitive = false }
}
