plugins {
    id("plexus.java-conventions")
}

description = "Public mod lifecycle API: mod entrypoint interfaces and the context passed to them."

dependencies {
    api(project(":plexus-runtime-api"))
    api(project(":plexus-system-api"))
    api(project(":plexus-event-api"))
    api(project(":plexus-game-api"))
}
