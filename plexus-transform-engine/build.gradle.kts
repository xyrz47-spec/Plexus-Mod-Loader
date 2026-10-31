plugins {
    id("plexus.java-conventions")
}

description = "ASM-based transformation planner and executor. Mods never depend on this module."

dependencies {
    api(project(":plexus-transform-api"))
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.asm.util)
}
