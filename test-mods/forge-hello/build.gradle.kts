plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "MinecraftForge mod written as for Forge: @Mod, the mod event bus, FMLCommonSetupEvent and a DeferredRegister item."

dependencies {
    // Forge's API comes from Forge's own files at run time (ADR-0021); the jar holds only the mod.
    compileOnly(variantOf(libs.forge.universal) { classifier("universal") }) { isTransitive = false }
    compileOnly(libs.forge.fmlcore) { isTransitive = false }
    compileOnly(libs.forge.fmlloader) { isTransitive = false }
    compileOnly(libs.forge.javafmllanguage) { isTransitive = false }
    compileOnly(libs.forge.eventbus) { isTransitive = false }
    compileOnly(libs.forge.forgespi) { isTransitive = false }
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
