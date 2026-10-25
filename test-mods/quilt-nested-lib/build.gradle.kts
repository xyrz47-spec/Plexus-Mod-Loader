plugins {
    id("poly.java-conventions")
}

description = "Quilt library nested inside quilt-hello, load_type if_required (the default); quilt_hello requires it."

dependencies {
    // Fabric Loader first: Quilt Loader's jar carries its own copies of Fabric Loader's classes.
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(libs.quilt.loader) { isTransitive = false }
    compileOnly(libs.slf4j.api)
}
