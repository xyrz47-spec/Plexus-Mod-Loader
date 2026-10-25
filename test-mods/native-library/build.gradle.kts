plugins {
    id("poly.java-conventions")
}

description = "Native library that Fabric and Native test mods depend on."

dependencies {
    compileOnly(project(":poly-lifecycle-api"))
    compileOnly(libs.slf4j.api)
}
