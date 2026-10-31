plugins {
    id("dev.plexusmodloader.mod") version "0.1.0-alpha.10"
}

group = "com.example"
version = "0.1.0"

// Standard resource files for simple content, generated at build time (generatePlexusResources):
// item definition and model, block state, block model, loot table, English name, tool tag.
// A file you write yourself under src/main/resources takes the place of the generated one.
plexusResources {
    // Without a texture setting these use textures/item/ruby.png and textures/block/ruby_block.png;
    // the template borrows vanilla textures so it works before you draw your own.
    item("ruby") { texture = "minecraft:item/redstone" }
    block("ruby_block") {
        texture = "minecraft:block/redstone_block"
        minedWith = "pickaxe"
    }
}

tasks.named<dev.plexusmodloader.devtools.gradle.minecraft.VerifiedGameRunTask>("worldCreateSaveTest") {
    expectedOutput.add("[my_mod] Hello from my_mod 0.1.0")
    expectedOutput.add("[my_mod] Server started")
}
