plugins {
    id("poly.java-conventions")
}

description = "Jar built for Fabric and NeoForge whose NeoForge side NeoForge refuses: Fabric runs it beside NeoForge."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
}
