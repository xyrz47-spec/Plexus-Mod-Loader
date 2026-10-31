plugins {
    id("plexus.java-conventions")
}

description = "Public Native gameplay API: content, world and entity views, commands, networking, client, and game tests."

dependencies {
    api(project(":plexus-runtime-api"))
    api(project(":plexus-event-api"))
}
