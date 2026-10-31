plugins {
    id("plexus.java-conventions")
}

description = "plexus.mod.json (and former poly.mod.json) model, parser, validator, and semantic version ranges."

dependencies {
    implementation(libs.gson)
}
