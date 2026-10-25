plugins {
    id("poly.java-conventions")
}

description = "System registry: built-in system definitions, default providers, replacement resolution, and conflicts."

dependencies {
    api(project(":poly-system-api"))
    implementation(project(":poly-mod-metadata"))
}
