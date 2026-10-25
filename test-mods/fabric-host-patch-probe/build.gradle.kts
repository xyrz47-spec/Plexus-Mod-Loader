plugins {
    id("poly.java-conventions")
}

description = "Fabric mod that exercises Fabric API features whose mixins meet members MinecraftForge's patch added or changed (corpusFabricForge)."

dependencies {
    // Mixin comes from Poly Mod Loader's class path at run time, as from Fabric Loader's. The game and Fabric API are
    // reached by reflection, so the probe compiles without them.
    compileOnly(libs.sponge.mixin) { isTransitive = false }
}
