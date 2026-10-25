plugins {
    id("poly.java-conventions")
}

description = "Fabric mod whose Mixin configurations change real 26.2 classes and its own classes, with MixinExtras."

dependencies {
    compileOnly(libs.fabric.loader) { isTransitive = false }
    // Mixin and MixinExtras come from Poly Mod Loader's class path at run time, as from Fabric Loader's.
    compileOnly(libs.sponge.mixin) { isTransitive = false }
    compileOnly(libs.mixinextras.common) { isTransitive = false }
    compileOnly(libs.asm.tree)
}

// The interfaces the class tweaker injects ship in the jar; the stubs stand in for the game classes the mixins
// target and implement those interfaces, as Loom's game jar does when compiling a real mod. The jar holds only the
// mod's own classes.
val injected = sourceSets.create("injected")
val stubs = sourceSets.create("stubs")
dependencies {
    "stubsCompileOnly"(injected.output)
    compileOnly(stubs.output)
    compileOnly(injected.output)
}
tasks.jar {
    from(injected.output)
}