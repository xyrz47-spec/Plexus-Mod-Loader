plugins {
    id("dev.polymodloader.mod") version "0.1.0-alpha.10"
}

group = "com.example"
version = "0.1.0"

// Standard resource files, generated at build time (generatePolyResources). The textures are borrowed
// from vanilla amethyst, so the mod works without drawing any. A file under src/main/resources wins:
// the crystal sprout's block state and item files are written by hand for its four growth stages.
polyResources {
    item("crystal") { texture = "minecraft:item/amethyst_shard" }
    block("crystal_block") {
        texture = "minecraft:block/amethyst_block"
        minedWith = "pickaxe"
    }
    block("crystal_sprout") {
        texture = "minecraft:block/budding_amethyst"
        minedWith = "pickaxe"
    }
}

tasks.named<dev.polymodloader.devtools.gradle.minecraft.VerifiedGameRunTask>("worldCreateSaveTest") {
    expectedOutput.add("[crystal_garden] Planting Crystal Garden 0.1.0")
}

// Opens the saved world in the client: the join check must let the player in.
tasks.named<dev.polymodloader.devtools.gradle.minecraft.ClientWorldReloadTask>("clientWorldReloadTest") {
    expectedOutput.add("[crystal_garden] Handshake with")
    expectedOutput.add("Configuration exchange crystal_garden:handshake with")
}
