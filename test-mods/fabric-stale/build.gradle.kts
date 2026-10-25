plugins {
    id("poly.java-conventions")
}

description = "Fabric mod whose code references Minecraft members 26.2 lacks; the link check must block it."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}

// The stubs only exist to compile against; the jar holds the mod's own classes, like a real stale mod.
val stubs = sourceSets.create("stubs")
dependencies {
    compileOnly(stubs.output)
}
