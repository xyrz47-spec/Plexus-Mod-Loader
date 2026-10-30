package dev.plexusmodloader.game.layer.v26_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import dev.plexusmodloader.api.content.entity.MobCategory;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.tag.TagKey;
import dev.plexusmodloader.api.worldgen.BiomeDefinition;
import dev.plexusmodloader.api.worldgen.GenerationStepName;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.TagEntriesDeclaration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratedDataPackTest {

    @Test
    void codeBiomesUseTheGameBiomeFileFormat() {
        JsonObject json = GeneratedDataPack.biomeJson(BiomeDefinition.builder()
                .climate(0.5f, 0.25f, false)
                .skyColor(0x123456)
                .grassColor(0x00ff00)
                .feature(GenerationStepName.VEGETAL_DECORATION, ResourceId.minecraft("flower_plains"))
                .spawn(MobCategory.CREATURE, ResourceId.minecraft("sheep"), 8, 2, 4)
                .build());

        assertEquals(
                "#123456",
                json.getAsJsonObject("attributes")
                        .get("minecraft:visual/sky_color")
                        .getAsString());
        assertEquals(
                "#00ff00", json.getAsJsonObject("effects").get("grass_color").getAsString());
        assertFalse(json.getAsJsonObject("effects").has("foliage_color"), "unset colours are left out");
        assertEquals(11, json.getAsJsonArray("features").size(), "one list per generation step");
        assertEquals(
                "minecraft:flower_plains",
                json.getAsJsonArray("features").get(9).getAsJsonArray().get(0).getAsString(),
                "vegetal decoration is step 9");
        JsonObject sheep = json.getAsJsonObject("spawners")
                .getAsJsonArray("creature")
                .get(0)
                .getAsJsonObject();
        assertEquals("minecraft:sheep", sheep.get("type").getAsString());
        assertEquals(4, sheep.get("maxCount").getAsInt());
        assertEquals(8, json.getAsJsonObject("spawners").size(), "every spawn group is present");
        assertFalse(json.get("has_precipitation").getAsBoolean());
        assertEquals(0.5f, json.get("temperature").getAsFloat());
    }

    @Test
    void tagEntriesFromCodeMergeIntoOneAddingFilePerTag() {
        TagKey pickaxe = TagKey.block(ResourceId.minecraft("mineable/pickaxe"));
        TagKey gems = TagKey.item(ResourceId.of("demo", "gems"));
        Map<TagKey, List<ResourceId>> files = GeneratedDataPack.tagFiles(List.of(
                new TagEntriesDeclaration("demo", pickaxe, List.of(ResourceId.of("demo", "ore"))),
                new TagEntriesDeclaration("demo", gems, List.of(ResourceId.of("demo", "ruby"))),
                new TagEntriesDeclaration(
                        "other", pickaxe, List.of(ResourceId.of("other", "brick"), ResourceId.of("demo", "ore")))));

        assertEquals(List.of(pickaxe, gems), List.copyOf(files.keySet()));
        assertEquals(List.of(ResourceId.of("demo", "ore"), ResourceId.of("other", "brick")), files.get(pickaxe));

        JsonObject json = GeneratedDataPack.tagJson(files.get(pickaxe));
        assertFalse(json.get("replace").getAsBoolean(), "code adds to the tag, it does not replace it");
        JsonObject first = json.getAsJsonArray("values").get(0).getAsJsonObject();
        assertEquals("demo:ore", first.get("id").getAsString());
        assertFalse(first.get("required").getAsBoolean(), "a missing entry must not break the whole tag");
        assertEquals("block", pickaxe.registry().directory());
        assertEquals(
                "entity_type",
                TagKey.entityType(ResourceId.minecraft("skeletons")).registry().directory());
    }
}
