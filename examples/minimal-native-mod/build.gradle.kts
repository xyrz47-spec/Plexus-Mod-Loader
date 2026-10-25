plugins {
    id("poly.java-conventions")
}

description = "Smallest Poly Mod: poly.mod.json plus a common and a client entrypoint."

dependencies {
    compileOnly(project(":poly-lifecycle-api"))
    compileOnly(libs.slf4j.api)
}
