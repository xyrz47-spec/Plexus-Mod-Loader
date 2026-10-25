plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that provides the language adapter 'poly_test' (languageAdapters)."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}
