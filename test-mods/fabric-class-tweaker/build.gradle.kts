plugins {
    id("poly.java-conventions")
}

description = "Fabric mod with a class tweaker that widens real 26.2 members and uses them from its entrypoint."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}

// The stubs stand in for the widened game classes, as Loom's widened game jar does when compiling a real mod;
// the jar holds only the mod's own classes, and the real members are widened when the game loads them.
val stubs = sourceSets.create("stubs")
dependencies {
    compileOnly(stubs.output)
}
