package dev.polymodloader.devtools.gradle.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModResourceGeneratorTest {

    @TempDir
    Path handWritten;

    private static ContentResourceDefinition item(String name) {
        return new ContentResourceDefinition(ContentResourceDefinition.Kind.ITEM, name, null, null, null, false);
    }

    private static ContentResourceDefinition block(String name, String minedWith) {
        return new ContentResourceDefinition(ContentResourceDefinition.Kind.BLOCK, name, null, null, minedWith, true);
    }

    private static JsonObject json(Map<String, String> files, String path) {
        assertTrue(files.containsKey(path), () -> path + " in " + files.keySet());
        return JsonParser.parseString(files.get(path)).getAsJsonObject();
    }

    @Test
    void itemGetsDefinitionFlatModelAndName() {
        Map<String, String> files = ModResourceGenerator.generate("ruby_mod", List.of(item("ruby_gem")), handWritten);

        assertEquals(
                "ruby_mod:item/ruby_gem",
                json(files, "assets/ruby_mod/items/ruby_gem.json")
                        .getAsJsonObject("model")
                        .get("model")
                        .getAsString());
        JsonObject model = json(files, "assets/ruby_mod/models/item/ruby_gem.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals(
                "ruby_mod:item/ruby_gem",
                model.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals(
                "Ruby Gem",
                json(files, "assets/ruby_mod/lang/en_us.json")
                        .get("item.ruby_mod.ruby_gem")
                        .getAsString());
        assertEquals(3, files.size());
    }

    @Test
    void blockGetsStateModelItemLootNameAndToolTag() {
        Map<String, String> files =
                ModResourceGenerator.generate("ruby_mod", List.of(block("ruby_block", "pickaxe")), handWritten);

        assertEquals(
                "ruby_mod:block/ruby_block",
                json(files, "assets/ruby_mod/blockstates/ruby_block.json")
                        .getAsJsonObject("variants")
                        .getAsJsonObject("")
                        .get("model")
                        .getAsString());
        assertEquals(
                "minecraft:block/cube_all",
                json(files, "assets/ruby_mod/models/block/ruby_block.json")
                        .get("parent")
                        .getAsString());
        assertEquals(
                "ruby_mod:block/ruby_block",
                json(files, "assets/ruby_mod/items/ruby_block.json")
                        .getAsJsonObject("model")
                        .get("model")
                        .getAsString());
        JsonObject loot = json(files, "data/ruby_mod/loot_table/blocks/ruby_block.json");
        assertEquals("minecraft:block", loot.get("type").getAsString());
        assertEquals("ruby_mod:blocks/ruby_block", loot.get("random_sequence").getAsString());
        assertTrue(files.get("data/ruby_mod/loot_table/blocks/ruby_block.json").contains("\"ruby_mod:ruby_block\""));
        assertEquals(
                "Ruby Block",
                json(files, "assets/ruby_mod/lang/en_us.json")
                        .get("block.ruby_mod.ruby_block")
                        .getAsString());
        JsonObject tag = json(files, "data/minecraft/tags/block/mineable/pickaxe.json");
        assertFalse(tag.get("replace").getAsBoolean());
        assertEquals("ruby_mod:ruby_block", tag.getAsJsonArray("values").get(0).getAsString());
    }

    @Test
    void handWrittenFileIsNotGenerated() throws IOException {
        write("assets/ruby_mod/models/block/ruby_block.json", "{\"parent\": \"minecraft:block/glass\"}");

        Map<String, String> files =
                ModResourceGenerator.generate("ruby_mod", List.of(block("ruby_block", null)), handWritten);

        assertFalse(files.containsKey("assets/ruby_mod/models/block/ruby_block.json"));
        assertTrue(files.containsKey("assets/ruby_mod/blockstates/ruby_block.json"));
        assertFalse(files.keySet().stream().anyMatch(path -> path.contains("mineable")));
    }

    @Test
    void langMergeKeepsHandWrittenEntriesAndValues() throws IOException {
        write(
                "assets/ruby_mod/lang/en_us.json",
                "{\"item.ruby_mod.ruby\": \"Shiny Ruby\", \"key.ruby_mod.open\": \"Open\"}");

        Map<String, String> files = ModResourceGenerator.generate(
                "ruby_mod", List.of(item("ruby"), block("ruby_block", null)), handWritten);

        JsonObject lang = json(files, "assets/ruby_mod/lang/en_us.json");
        assertEquals("Shiny Ruby", lang.get("item.ruby_mod.ruby").getAsString());
        assertEquals("Open", lang.get("key.ruby_mod.open").getAsString());
        assertEquals("Ruby Block", lang.get("block.ruby_mod.ruby_block").getAsString());
    }

    @Test
    void langWithEveryNameAlreadyWrittenIsLeftAlone() throws IOException {
        write("assets/ruby_mod/lang/en_us.json", "{\"item.ruby_mod.ruby\": \"Shiny Ruby\"}");

        Map<String, String> files = ModResourceGenerator.generate("ruby_mod", List.of(item("ruby")), handWritten);

        assertFalse(files.containsKey("assets/ruby_mod/lang/en_us.json"));
    }

    @Test
    void toolTagMergeKeepsHandWrittenValues() throws IOException {
        write("data/minecraft/tags/block/mineable/pickaxe.json", "{\"replace\": false, \"values\": [\"ruby_mod:old\"]}");

        Map<String, String> files =
                ModResourceGenerator.generate("ruby_mod", List.of(block("ruby_block", "pickaxe")), handWritten);

        String values = json(files, "data/minecraft/tags/block/mineable/pickaxe.json")
                .getAsJsonArray("values")
                .toString();
        assertEquals("[\"ruby_mod:old\",\"ruby_mod:ruby_block\"]", values);
    }

    @Test
    void settingsChangeTextureNameAndDrop() {
        ContentResourceDefinition lamp = new ContentResourceDefinition(
                ContentResourceDefinition.Kind.BLOCK,
                "lamp",
                "Ruby Lamp",
                "minecraft:block/redstone_lamp_on",
                null,
                false);

        Map<String, String> files = ModResourceGenerator.generate("ruby_mod", List.of(lamp), handWritten);

        assertEquals(
                "minecraft:block/redstone_lamp_on",
                json(files, "assets/ruby_mod/models/block/lamp.json")
                        .getAsJsonObject("textures")
                        .get("all")
                        .getAsString());
        assertEquals(
                "Ruby Lamp",
                json(files, "assets/ruby_mod/lang/en_us.json")
                        .get("block.ruby_mod.lamp")
                        .getAsString());
        assertFalse(files.containsKey("data/ruby_mod/loot_table/blocks/lamp.json"));
    }

    @Test
    void textureWithoutNamespaceIsInTheModNamespace() {
        ContentResourceDefinition gem =
                new ContentResourceDefinition(ContentResourceDefinition.Kind.ITEM, "gem", null, "item/red", null, false);

        assertEquals("ruby_mod:item/red", gem.textureId("ruby_mod"));
    }

    @Test
    void sameNameTwiceIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ModResourceGenerator.generate(
                        "ruby_mod", List.of(item("ruby"), block("ruby", null)), handWritten));

        assertTrue(error.getMessage().startsWith("PLEXUS-RESOURCE-009"), error.getMessage());
    }

    @Test
    void invalidDeclarationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> item("Ruby"));
        assertThrows(IllegalArgumentException.class, () -> block("ruby_block", "sword"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContentResourceDefinition(
                        ContentResourceDefinition.Kind.ITEM, "ruby", null, "Item/Ruby", null, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContentResourceDefinition(
                        ContentResourceDefinition.Kind.ITEM, "ruby", null, null, "pickaxe", false));
    }

    private void write(String path, String text) throws IOException {
        Path file = handWritten.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
