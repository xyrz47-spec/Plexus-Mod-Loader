plugins {
    id("poly.java-conventions")
}

description = "Fabric mod built for Minecraft 1.21.4; it must be blocked, not crash."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}
