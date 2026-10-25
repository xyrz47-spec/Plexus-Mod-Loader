plugins {
    id("poly.java-conventions")
}

description = "Public system replacement API: system IDs, providers, lookup, and built-in system contracts."

dependencies {
    api(project(":poly-runtime-api"))
}
