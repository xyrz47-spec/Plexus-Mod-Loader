plugins {
    id("poly.java-conventions")
}

description = "Diagnosis harness: applies every installed mod's mixins for this side to their targets and stops before the game starts."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.sponge.mixin) { isTransitive = false }
}
