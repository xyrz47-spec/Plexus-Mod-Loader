plugins {
    id("poly.java-conventions")
}

description = "Public event API: typed event keys, listener registration, and server lifecycle events."

dependencies {
    api(project(":plexus-runtime-api"))
}
