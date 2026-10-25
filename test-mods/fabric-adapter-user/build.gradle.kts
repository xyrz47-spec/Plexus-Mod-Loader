plugins {
    id("poly.java-conventions")
}

description = "Fabric mod whose main entrypoint is created by the 'poly_test' language adapter."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}
