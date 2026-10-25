plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "Fabric client mod that plays in the world for a while, then saves and quits as a player does."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    // Mixin comes from Poly Mod Loader's class path at run time, as from Fabric Loader's.
    compileOnly(libs.sponge.mixin) { isTransitive = false }
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
