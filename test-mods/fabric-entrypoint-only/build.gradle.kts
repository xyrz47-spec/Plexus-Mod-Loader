plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that only uses entrypoints; loaded through the Poly Mod Loader Fabric bridge in tests."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}
