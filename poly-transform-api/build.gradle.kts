plugins {
    id("poly.java-conventions")
}

description = "Declarations for class transformation rules (target, action, failure policy) and their errors."

dependencies {
    api(project(":poly-runtime-api"))
}
