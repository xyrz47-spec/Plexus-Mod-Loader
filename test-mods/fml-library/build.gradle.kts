plugins {
    id("poly.java-conventions")
}

description = "NeoForge library jar (FMLModType in the manifest, no mods.toml); it must be blocked, not called a non-mod."

dependencies {

}

tasks.jar {
    manifest {
        attributes("FMLModType" to "GAMELIBRARY")
    }
}
