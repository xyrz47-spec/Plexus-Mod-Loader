plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that uses Fabric Loader's internal FabricLauncherBase, which Poly Mod Loader does not provide; the link check must block it."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}
