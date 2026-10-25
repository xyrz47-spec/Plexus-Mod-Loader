plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "NeoForge side of the headless join check: a play-phase payload of its own, sent to each player who joins and echoed back by the server."

dependencies {
    // NeoForge's API comes from NeoForge's own files at run time (ADR-0026); the jar holds only the mod.
    compileOnly(variantOf(libs.neoforge.universal) { classifier("universal") }) { isTransitive = false }
    compileOnly(libs.neoforge.fml.loader) { isTransitive = false }
    compileOnly(libs.neoforge.bus) { isTransitive = false }
    compileOnly(variantOf(libs.neoforge.mergetool.api) { classifier("api") }) { isTransitive = false }
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
