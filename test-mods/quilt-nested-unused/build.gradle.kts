plugins {
    id("poly.java-conventions")
}

description = "Quilt mod nested inside quilt-hello, load_type if_required (the default), which no mod requires."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}
