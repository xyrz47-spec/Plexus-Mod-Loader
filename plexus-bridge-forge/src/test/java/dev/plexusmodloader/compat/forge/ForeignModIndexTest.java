package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-0025: which mods FML's mod list answers for beside Forge's own, and the mods Forge runs as others see them. */
class ForeignModIndexTest {

    @TempDir
    Path directory;

    private static BridgeLaunchContext.LoadedMod fabric(String id, String version, Path jar, String... provides) {
        return new BridgeLaunchContext.LoadedMod("Fabric", id, version, id, List.of(provides), jar, null, null);
    }

    @Test
    void modsAreFoundByTheirOwnAndTheirProvidedIds() throws IOException {
        Path sodium = jar("sodium.jar", Map.of("fabric.mod.json", """
                {"schemaVersion": 1, "id": "sodium", "version": "0.9.2+mc26.2", "name": "Sodium",
                 "provides": ["indium"]}
                """));
        BridgeLaunchContext.LoadedMod poly = new BridgeLaunchContext.LoadedMod(
                "Poly Mod Loader",
                "native_lib",
                "2.1.0",
                "Native Library",
                List.of(),
                jar("poly.jar", Map.of()),
                null,
                null);

        ForeignModIndex index =
                ForeignModIndex.of(List.of(fabric("sodium", "0.9.2+mc26.2", sodium, "indium"), poly), List.of());

        ForeignModIndex.ForeignMod found = index.find("sodium").orElseThrow();
        assertEquals("Fabric", found.loader());
        assertEquals("0.9.2+mc26.2", found.version());
        assertEquals("Sodium", found.displayName(), "the name comes from fabric.mod.json");
        assertEquals(sodium, found.jar());
        assertEquals(List.of("sodium", "indium"), found.ids());
        assertEquals(found, index.find("indium").orElseThrow(), "a provided ID answers with the mod that provides it");
        assertEquals("Native Library", index.find("native_lib").orElseThrow().displayName());
        assertTrue(index.find("lithium").isEmpty());
        assertEquals(2, index.mods().size());
    }

    /** A Quilt mod's name is {@code quilt_loader.metadata.name}; without one, the ID, as in Quilt Loader. */
    @Test
    void aQuiltModsNameComesFromQuiltModJson() throws IOException {
        Path named = jar("named.jar", Map.of("quilt.mod.json", """
                {"schema_version": 1, "quilt_loader": {"group": "dev.example", "id": "quilt_hello",
                 "version": "1.0.0", "metadata": {"name": "Quilt Hello"}}}
                """));
        Path unnamed = jar("unnamed.jar", Map.of("quilt.mod.json", """
                {"schema_version": 1, "quilt_loader": {"group": "dev.example", "id": "quilt_plain", "version": "1.0.0"}}
                """));

        ForeignModIndex index = ForeignModIndex.of(
                List.of(
                        new BridgeLaunchContext.LoadedMod(
                                "Quilt", "quilt_hello", "1.0.0", "quilt_hello", List.of(), named, null, null),
                        new BridgeLaunchContext.LoadedMod(
                                "Quilt", "quilt_plain", "1.0.0", "quilt_plain", List.of(), unnamed, null, null)),
                List.of());

        assertEquals("Quilt Hello", index.find("quilt_hello").orElseThrow().displayName());
        assertEquals("Quilt", index.find("quilt_hello").orElseThrow().loader());
        assertEquals("quilt_plain", index.find("quilt_plain").orElseThrow().displayName());
    }

    @Test
    void forgeKeepsItsIds() throws IOException {
        Path jar = jar("both.jar", Map.of());
        BridgeLaunchContext.LoadedMod hostRun = new BridgeLaunchContext.LoadedMod(
                "MinecraftForge", "physicsmod", "3.2.4", "Physics Mod", List.of(), jar, null, null, true);

        ForeignModIndex index = ForeignModIndex.of(
                List.of(
                        hostRun,
                        fabric("geckolib", "5.5.6", jar),
                        fabric("tweaks", "1.0", jar, "forge_lib", "tweaks_alias")),
                List.of("geckolib", "forge_lib"));

        assertTrue(index.find("physicsmod").isEmpty(), "a mod Forge runs is FML's own");
        assertTrue(index.find("geckolib").isEmpty(), "a Forge mod's ID stays the Forge mod's");
        assertTrue(index.find("forge_lib").isEmpty(), "so does a provided ID that a Forge mod has");
        assertEquals(
                List.of("tweaks", "tweaks_alias"),
                index.find("tweaks_alias").orElseThrow().ids());
    }

    @Test
    void aModsOwnIdWinsOverAnotherModsProvidedId() throws IOException {
        Path jar = jar("mods.jar", Map.of());

        ForeignModIndex index = ForeignModIndex.of(
                List.of(fabric("compat", "1.0", jar, "real"), fabric("real", "2.0", jar), fabric("real", "3.0", jar)),
                List.of());

        assertEquals("2.0", index.find("real").orElseThrow().version(), "the first mod with that ID answers");
        assertEquals(List.of("compat"), index.find("compat").orElseThrow().ids());
        assertEquals(2, index.mods().size());
    }

    @Test
    void theModsForgeRunsAreItsModsTomlEntriesThenTheNestedOnes() throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "1.4.0");
        byte[] nested =
                jarBytes(manifest, Map.of("META-INF/mods.toml", modsToml("inner_lib", "${file.jarVersion}", null)));
        Path outer = jar(
                "outer.jar",
                Map.of(
                        "META-INF/mods.toml",
                        modsToml("outer", "2.0.0", "Outer Mod")
                                + "\n[[mods]]\nmodId=\"outer_addon\"\nversion=\"2.0.1\"\n",
                        "META-INF/jarjar/inner_lib-1.4.0.jar",
                        nested));
        Path serviceOnly = jar("service.jar", Map.of());

        List<BridgeLaunchContext.LoadedMod> mods = ForgeGameHost.hostMods(List.of(outer, serviceOnly));

        assertEquals(
                List.of(
                        new BridgeLaunchContext.LoadedMod(
                                "MinecraftForge", "outer", "2.0.0", "Outer Mod", List.of(), outer, null, null, true),
                        new BridgeLaunchContext.LoadedMod(
                                "MinecraftForge",
                                "outer_addon",
                                "2.0.1",
                                "outer_addon",
                                List.of(),
                                outer,
                                null,
                                null,
                                true),
                        new BridgeLaunchContext.LoadedMod(
                                "MinecraftForge",
                                "inner_lib",
                                "1.4.0",
                                "inner_lib",
                                List.of(),
                                outer,
                                outer,
                                "META-INF/jarjar/inner_lib-1.4.0.jar",
                                true)),
                mods);
    }

    private static String modsToml(String id, String version, String displayName) {
        return """
                modLoader="javafml"
                loaderVersion="[65,)"
                license="MIT"
                [[mods]]
                modId="%s"
                version="%s"
                """.formatted(id, version) + (displayName == null ? "" : "displayName=\"" + displayName + "\"\n");
    }

    private Path jar(String name, Map<String, ?> entries) throws IOException {
        Path path = directory.resolve(name);
        Files.write(path, jarBytes(null, entries));
        return path;
    }

    private static byte[] jarBytes(Manifest manifest, Map<String, ?> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = bytes;
                JarOutputStream jar =
                        manifest == null ? new JarOutputStream(out) : new JarOutputStream(out, manifest)) {
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(
                        entry.getValue() instanceof byte[] raw
                                ? raw
                                : entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
