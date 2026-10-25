plugins {
    id("poly.java-conventions")
}

description = "Declares class transformations and an access widening in poly.mod.json to demonstrate mod-owned transforms."

dependencies {
    compileOnly(libs.slf4j.api)
}
