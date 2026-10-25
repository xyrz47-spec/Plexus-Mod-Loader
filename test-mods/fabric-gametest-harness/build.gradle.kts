plugins {
    id("poly.java-conventions")
}

description = "Test harness for corpus runs: freezes registries on Mojang's game test server where Fabric API does it on the dedicated server."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.sponge.mixin) { isTransitive = false }
}

// Stand-ins for the 26.2 classes the mixin touches; never packaged.
val stubs = sourceSets.create("stubs")
dependencies {
    compileOnly(stubs.output)
}