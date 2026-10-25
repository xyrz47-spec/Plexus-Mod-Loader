plugins {
    id("poly.java-conventions")
}

description = "Mod jar with both fabric.mod.json and quilt.mod.json (same ID, different entrypoints); runs as a Fabric mod."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}
