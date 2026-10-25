plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "Fabric client harness that stops the client right before Minecraft creates the game window, after mod initialization, for headless checks."

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
