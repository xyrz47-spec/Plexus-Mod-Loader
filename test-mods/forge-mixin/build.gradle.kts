plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "MinecraftForge mod whose Mixin configuration (manifest MixinConfigs) changes real 26.2 classes, as Forge's Mixin applies it."

dependencies {
    // Forge's API and Mixin come from Forge's own files at run time (ADR-0021); the jar holds only the mod.
    compileOnly(libs.forge.fmlcore) { isTransitive = false }
    compileOnly(libs.forge.javafmllanguage) { isTransitive = false }
    compileOnly(libs.forge.eventbus) { isTransitive = false }
    compileOnly(libs.sponge.mixin) { isTransitive = false }
}

tasks.jar {
    manifest { attributes("MixinConfigs" to "forge_mixin.mixins.json") }
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
