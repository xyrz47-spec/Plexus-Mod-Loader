plugins {
    id("poly.java-conventions")
}

description = "System registry: built-in system definitions, default providers, replacement resolution, and conflicts."

dependencies {
    api(project(":plexus-system-api"))
    implementation(project(":plexus-mod-metadata"))
}
