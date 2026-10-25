plugins {
    id("poly.java-conventions")
    id("poly.minecraft-compile")
}

description = "NeoForge mod whose Mixin configurations (neoforge.mods.toml [[mixins]]) change real 26.2 classes, one with MixinExtras."

dependencies {
    // NeoForge's API, Mixin and MixinExtras come from NeoForge's or Poly Mod Loader's files at run time (ADR-0026).
    compileOnly(libs.neoforge.fml.loader) { isTransitive = false }
    compileOnly(variantOf(libs.neoforge.mergetool.api) { classifier("api") }) { isTransitive = false }
    compileOnly(libs.sponge.mixin) { isTransitive = false }
    compileOnly(libs.mixinextras.common) { isTransitive = false }
}

// The game jar references JetBrains annotations that are not on the class path; javac warns about
// every use (classfile lint), which -Werror would turn into errors.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
