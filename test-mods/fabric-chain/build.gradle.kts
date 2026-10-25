plugins {
    id("poly.java-conventions")
}

description = "Fabric mod whose dependencies are blocked or absent; it must be blocked with the reasons."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}
