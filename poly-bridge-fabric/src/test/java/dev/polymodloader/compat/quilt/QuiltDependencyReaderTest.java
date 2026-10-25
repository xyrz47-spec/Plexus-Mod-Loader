package dev.polymodloader.compat.quilt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.polymodloader.compat.bridge.BridgedDependency;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.quiltmc.loader.api.ModDependency;

class QuiltDependencyReaderTest {

    private static QuiltDependencyReader.Read depends(String json) throws QuiltMetadataException {
        return QuiltDependencyReader.depends(JsonParser.parseString(json));
    }

    private static QuiltDependencyReader.Read breaks(String json) throws QuiltMetadataException {
        return QuiltDependencyReader.breaks(JsonParser.parseString(json));
    }

    @Test
    void aTextIsAnyVersionOfThatMod() throws QuiltMetadataException {
        QuiltDependencyReader.Read read = depends("\"fabric_lib\"");

        assertEquals("fabric_lib", read.bridged().modId());
        assertEquals(BridgedDependency.Kind.REQUIRED, read.bridged().kind());
        assertTrue(read.bridged().accepts().test("0.0.1"));
        assertTrue(read.bridged().skipWhenOtherSideOnly());
        ModDependency.Only quilt = assertInstanceOf(ModDependency.Only.class, read.quilt());
        assertEquals("fabric_lib", quilt.id().id());
        assertEquals("", quilt.id().mavenGroup());
        assertFalse(quilt.optional());
    }

    @Test
    void aGroupPrefixStaysInTheQuiltFormOnly() throws QuiltMetadataException {
        QuiltDependencyReader.Read read = depends("\"org.example:lib\"");

        assertEquals("lib", read.bridged().modId());
        ModDependency.Only quilt = (ModDependency.Only) read.quilt();
        assertEquals("org.example", quilt.id().mavenGroup());
        assertEquals("lib", quilt.id().id());
    }

    @Test
    void anObjectHasVersionsReasonOptionalAndUnless() throws QuiltMetadataException {
        QuiltDependencyReader.Read read =
                depends("{\"id\": \"fabric_lib\", \"versions\": \"1.0.0\", \"reason\": \"for X\", \"optional\": true,"
                        + " \"unless\": \"other\"}");

        BridgedDependency bridged = read.bridged();
        assertEquals(BridgedDependency.Kind.IF_PRESENT, bridged.kind());
        assertTrue(bridged.accepts().test("1.2.0"), "Quilt reads 1.0.0 as ^1.0.0");
        assertFalse(bridged.accepts().test("2.0.0"));
        assertTrue(bridged.requirement().contains("1.0.0 (optional) unless other"), bridged.requirement());
        assertTrue(bridged.requirement().contains("for X"), bridged.requirement());
        assertEquals(1, bridged.unless().size());
        assertEquals("other", bridged.unless().getFirst().modId());
        ModDependency.Only quilt = (ModDependency.Only) read.quilt();
        assertTrue(quilt.optional());
        assertEquals("for X", quilt.reason());
        assertEquals("other", ((ModDependency.Only) quilt.unless()).id().id());
        assertTrue(quilt.matches(org.quiltmc.loader.api.Version.of("1.5.0")));
    }

    @Test
    void anObjectWithoutVersionsAcceptsEveryVersion() throws QuiltMetadataException {
        QuiltDependencyReader.Read read = depends("{\"id\": \"lib\"}");

        assertEquals(BridgedDependency.Kind.REQUIRED, read.bridged().kind());
        assertTrue(read.bridged().accepts().test("anything"));
    }

    @Test
    void unlessMayBeAnObjectOrAnArray() throws QuiltMetadataException {
        BridgedDependency object = depends("{\"id\": \"lib\", \"unless\": {\"id\": \"other\", \"versions\": \">=2\"}}")
                .bridged();
        BridgedDependency array =
                depends("{\"id\": \"lib\", \"unless\": [\"a\", \"b\"]}").bridged();

        assertEquals("other", object.unless().getFirst().modId());
        assertFalse(object.unless().getFirst().accepts().test("1.0.0"));
        assertTrue(array.unless().getFirst().isGroup());
        assertEquals(
                List.of("a", "b"),
                array.unless().getFirst().members().stream()
                        .map(BridgedDependency::modId)
                        .toList());
    }

    @Test
    void anArrayInDependsIsAnyAndInBreaksIsAll() throws QuiltMetadataException {
        QuiltDependencyReader.Read any = depends("[{\"id\": \"absent_a\"}, \"quilt_loader\", [\"c\", \"d\"]]");
        QuiltDependencyReader.Read all = breaks("[{\"id\": \"fabric_lib\"}, {\"id\": \"absent_b\"}]");

        assertTrue(any.bridged().isGroup());
        assertEquals(BridgedDependency.Kind.REQUIRED, any.bridged().kind());
        assertEquals(3, any.bridged().members().size());
        assertTrue(any.bridged().members().get(2).isGroup(), "nested arrays stay nested");
        assertTrue(
                any.bridged().requirement().startsWith("any of ["),
                any.bridged().requirement());
        ModDependency.Any quiltAny = assertInstanceOf(ModDependency.Any.class, any.quilt());
        assertEquals(4, quiltAny.size(), "flattened");

        assertEquals(BridgedDependency.Kind.BREAKS, all.bridged().kind());
        assertTrue(all.bridged().members().stream().allMatch(member -> member.kind() == BridgedDependency.Kind.BREAKS));
        assertInstanceOf(ModDependency.All.class, all.quilt());
    }

    @Test
    void anEmptyArrayIsIgnored() throws QuiltMetadataException {
        assertEquals(BridgedDependency.Kind.OPTIONAL, depends("[]").bridged().kind());
        assertEquals(BridgedDependency.Kind.OPTIONAL, breaks("[]").bridged().kind());
    }

    @Test
    void optionalMeansNothingInBreaks() throws QuiltMetadataException {
        assertEquals(
                BridgedDependency.Kind.BREAKS,
                breaks("{\"id\": \"x\", \"optional\": true}").bridged().kind());
    }

    @Test
    void malformedObjectsAreRefused() {
        for (String bad : List.of(
                "5",
                "true",
                "null",
                "{}",
                "{\"id\": 5}",
                "{\"id\": \"x\", \"optional\": \"yes\"}",
                "{\"id\": \"x\", \"reason\": 5}",
                "{\"id\": \"x\", \"versions\": \"~abc\"}",
                "{\"id\": \"x\", \"versions\": \"1.x.x\"}",
                "{\"id\": \"x\", \"versions\": {\"all\": []}}",
                "{\"id\": \"x\", \"unless\": 5}",
                "[\"ok\", 5]")) {
            assertThrows(QuiltMetadataException.class, () -> depends(bad), bad);
        }
    }

    @Test
    void aRequiredQslModuleIsNoted() throws QuiltMetadataException {
        List<String> required =
                depends("[\"quilt_resource_loader\", {\"id\": \"qsl\"}, \"quilted_fabric_api\"]").findings().stream()
                        .map(CompatibilityFinding::code)
                        .toList();
        List<String> notQsl =
                depends("[\"quilt_loader\", {\"id\": \"quilt_base\", \"optional\": true}]").findings().stream()
                        .map(CompatibilityFinding::code)
                        .toList();

        assertEquals(List.of("POLY-COMPAT-308", "POLY-COMPAT-308", "POLY-COMPAT-308"), required);
        assertEquals(List.of(), notQsl);
        assertEquals(List.of(), breaks("\"quilt_base\"").findings());
        assertFalse(depends("\"quilt_resource_loader\"").findings().getFirst().blocking());
    }

    @Test
    void aQuiltPrefixAloneIsNotAQslModule() throws QuiltMetadataException {
        // Regression: quilt_hello's own nested library quilt_nested_lib got "Quilt Loader cannot meet this dependency
        // either" although it is installed and loads (ADR-0027 Q3 note).
        assertEquals(
                List.of(),
                depends("{\"id\": \"quilt_nested_lib\", \"versions\": \">=1.0.0\"}")
                        .findings());
        assertEquals(List.of(), depends("\"quilt_hello\"").findings());
        assertEquals(
                List.of("POLY-COMPAT-308", "POLY-COMPAT-308", "POLY-COMPAT-308"),
                depends("[\"quilt_base\", {\"id\": \"quilt_resource_loader\"}, \"quilted_fabric_api_base\"]")
                        .findings()
                        .stream()
                        .map(CompatibilityFinding::code)
                        .toList());
    }

    @Test
    void theQslModuleIdsComeFromQslSources() {
        // Modules of old QSL branches and the ID quilt_registry_entry_attachment provides count too.
        for (String id : List.of(
                "quilt_base",
                "quilt_resource_loader",
                "quilt_lifecycle_events",
                "quilt_entity_networking",
                "quilt_registry_entry_attachments",
                "qsl",
                "quilted_fabric_api",
                "quilted_fabric_item_group_api_v1")) {
            assertTrue(QslModules.isQslModule(id), id);
        }
        for (String id : List.of("quilt_loader", "quilt_nested_lib", "quilt_hello", "quilted", "fabric-api-base")) {
            assertFalse(QslModules.isQslModule(id), id);
        }
    }
}
