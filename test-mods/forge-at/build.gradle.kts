plugins {
    id("poly.java-conventions")
}

description = "MinecraftForge mod whose access transformer widens real 26.2 members and uses them from its constructor."

dependencies {
    // Forge's API comes from Forge's own files at run time (ADR-0021); the jar holds only the mod.
    compileOnly(libs.forge.fmlcore) { isTransitive = false }
    compileOnly(libs.forge.javafmllanguage) { isTransitive = false }
    compileOnly(libs.forge.eventbus) { isTransitive = false }
}

// The stubs stand in for the widened game classes, as ForgeGradle's access-transformed game jar does when compiling a
// real mod; the jar holds only the mod's own classes, and the real members are widened when the game loads them.
val stubs = sourceSets.create("stubs")
dependencies {
    compileOnly(stubs.output)
}
