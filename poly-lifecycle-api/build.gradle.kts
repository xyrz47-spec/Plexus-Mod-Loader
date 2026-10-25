plugins {
    id("poly.java-conventions")
}

description = "Public mod lifecycle API: mod entrypoint interfaces and the context passed to them."

dependencies {
    api(project(":poly-runtime-api"))
    api(project(":poly-system-api"))
    api(project(":poly-event-api"))
    api(project(":poly-game-api"))
}
