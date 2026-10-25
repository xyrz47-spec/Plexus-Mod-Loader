plugins {
    id("poly.java-conventions")
}

description = "Entrypoint-only Fabric library that Fabric and Native test mods depend on."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}
