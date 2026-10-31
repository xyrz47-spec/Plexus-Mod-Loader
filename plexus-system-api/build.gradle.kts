plugins {
    id("plexus.java-conventions")
}

description = "Public system replacement API: system IDs, providers, lookup, and built-in system contracts."

dependencies {
    api(project(":plexus-runtime-api"))
}
