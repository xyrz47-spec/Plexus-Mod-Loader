plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that requires a Fabric library and a Native library."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
    compileOnly(project(":test-mods:fabric-library"))
    compileOnly(project(":test-mods:native-library"))
    // NativeLib implements a Poly Mod Loader interface, so compiling against it needs that interface too.
    compileOnly(project(":poly-lifecycle-api"))
}
