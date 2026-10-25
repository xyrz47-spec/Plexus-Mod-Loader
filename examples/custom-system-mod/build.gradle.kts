plugins {
    id("poly.java-conventions")
}

description = "Replaces the poly:game_brand system to demonstrate system replacement (Phase 2)."

dependencies {
    compileOnly(project(":poly-system-api"))
}
