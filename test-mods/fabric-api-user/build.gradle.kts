plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that uses FabricLoader.getInstance() like real mods: mod lookup, metadata, entrypoints, object share."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}
