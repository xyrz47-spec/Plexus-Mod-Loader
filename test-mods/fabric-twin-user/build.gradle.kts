plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "Fabric mod that requires twin-mod (fabric-twin) and uses its shared class, whichever build of it runs."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    compileOnly(project(":test-mods:fabric-twin"))
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
