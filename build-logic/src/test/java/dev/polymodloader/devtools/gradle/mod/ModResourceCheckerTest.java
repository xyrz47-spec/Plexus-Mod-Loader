package dev.polymodloader.devtools.gradle.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModResourceCheckerTest {

    @TempDir
    Path root;

    private void write(String path, String text) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String only(List<ModResourceProblem> problems, String code) {
        assertEquals(1, problems.size(), problems::toString);
        assertEquals(code, problems.getFirst().code(), problems::toString);
        return problems.getFirst().toString();
    }

    @Test
    void uppercaseFileNameIsReported() throws IOException {
        write("assets/ruby_mod/textures/item/Ruby.png", "");

        String problem = only(ModResourceChecker.check(root), "POLY-RESOURCE-004");
        assertTrue(problem.contains("assets/ruby_mod/textures/item/ruby.png"), problem);
    }

    @Test
    void trailingCommaIsReportedWithItsPosition() throws IOException {
        write("assets/ruby_mod/lang/en_us.json", "{\n  \"item.ruby_mod.ruby\": \"Ruby\",\n}\n");

        String problem = only(ModResourceChecker.check(root), "POLY-RESOURCE-005");
        assertTrue(problem.contains("line 3"), problem);
        assertTrue(!problem.contains("setStrictness"), problem);
    }

    @Test
    void missingItemModelIsReported() throws IOException {
        write("assets/ruby_mod/items/ruby.json", "{\"model\": {\"type\": \"minecraft:model\", \"model\": \"ruby_mod:item/rubyy\"}}");
        write("assets/ruby_mod/models/item/ruby.json", "{\"parent\": \"minecraft:item/generated\"}");

        String problem = only(ModResourceChecker.check(root), "POLY-RESOURCE-006");
        assertTrue(problem.contains("assets/ruby_mod/models/item/rubyy.json"), problem);
    }

    @Test
    void missingTextureIsReported() throws IOException {
        write(
                "assets/ruby_mod/models/block/ruby_block.json",
                "{\"parent\": \"minecraft:block/cube_all\", \"textures\": {\"all\": \"ruby_mod:block/ruby_block\","
                        + " \"particle\": \"#all\"}}");

        String problem = only(ModResourceChecker.check(root), "POLY-RESOURCE-007");
        assertTrue(problem.contains("assets/ruby_mod/textures/block/ruby_block.png"), problem);
    }

    @Test
    void referenceWithoutNamespaceToTheModsOwnFileIsReported() throws IOException {
        write("assets/ruby_mod/models/item/ruby.json", "{\"parent\": \"minecraft:item/generated\", \"textures\":"
                + " {\"layer0\": \"item/ruby\"}}");
        write("assets/ruby_mod/textures/item/ruby.png", "");

        String problem = only(ModResourceChecker.check(root), "POLY-RESOURCE-007");
        assertTrue(problem.contains("\"ruby_mod:item/ruby\""), problem);
    }

    @Test
    void blockStateVariantsAndMultipartAreChecked() throws IOException {
        write(
                "assets/ruby_mod/blockstates/lamp.json",
                "{\"multipart\": [{\"apply\": [{\"model\": \"ruby_mod:block/lamp_on\"}, {\"model\":"
                        + " \"minecraft:block/stone\"}]}]}");

        String problem = only(ModResourceChecker.check(root), "POLY-RESOURCE-006");
        assertTrue(problem.contains("lamp_on"), problem);
    }

    @Test
    void langValueThatIsNotTextIsReported() throws IOException {
        write("assets/ruby_mod/lang/en_us.json", "{\"item.ruby_mod.ruby\": 3}");

        only(ModResourceChecker.check(root), "POLY-RESOURCE-008");
    }

    @Test
    void vanillaAndOtherModReferencesAreNotChecked() throws IOException {
        write(
                "assets/ruby_mod/models/block/crate.json",
                "{\"parent\": \"block/cube_column\", \"textures\": {\"end\": \"minecraft:block/barrel_top\","
                        + " \"side\": \"other_mod:block/side\"}}");

        assertEquals(List.of(), ModResourceChecker.check(root));
    }

    @Test
    void generatedResourcesWithTheirTexturesHaveNoProblems() throws IOException {
        List<ContentResourceDefinition> content = List.of(
                new ContentResourceDefinition(ContentResourceDefinition.Kind.ITEM, "ruby", null, null, null, false),
                new ContentResourceDefinition(
                        ContentResourceDefinition.Kind.BLOCK, "ruby_block", null, null, "pickaxe", true));
        for (Map.Entry<String, String> file :
                ModResourceGenerator.generate("ruby_mod", content, root.resolve("none")).entrySet()) {
            write(file.getKey(), file.getValue());
        }
        write("assets/ruby_mod/textures/item/ruby.png", "");
        write("assets/ruby_mod/textures/block/ruby_block.png", "");

        assertEquals(List.of(), ModResourceChecker.check(root));
    }

    /** The showcase mod's resources load in the game (its game tests pass), so none may be reported. */
    @Test
    void showcaseModResourcesHaveNoProblems() {
        Path showcase = Path.of("../examples/native-showcase-mod/src/main/resources");
        assertTrue(Files.isDirectory(showcase), showcase.toAbsolutePath().toString());

        assertEquals(List.of(), ModResourceChecker.check(showcase));
    }
}
