package dev.plexusmodloader.runtime.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.runtime.discovery.ForeignModResolver.ForeignMod;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NestedJarExtractorTest {

    @TempDir
    Path directory;

    private URLClassLoader bridgeLoader;
    private ForeignModResolver bridges;

    /** Registers {@link TestJarInJarBridge} through a services file, as a real bridge jar does. */
    @BeforeEach
    void installBridge() throws IOException {
        Path services = directory.resolve("bridge/META-INF/services/dev.plexusmodloader.compat.bridge.ModLoaderBridge");
        Files.createDirectories(services.getParent());
        Files.writeString(
                services, TestJarInJarBridge.class.getName() + "\n" + TestQuiltJarInJarBridge.class.getName());
        bridgeLoader = new URLClassLoader(
                new URL[] {directory.resolve("bridge").toUri().toURL()},
                getClass().getClassLoader());
        bridges = new ForeignModResolver(bridgeLoader);
    }

    @AfterEach
    void closeBridge() throws IOException {
        bridgeLoader.close();
    }

    private static byte[] jar(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] fabricMod(String id, String version, Map<String, byte[]> nested) throws IOException {
        StringBuilder jars = new StringBuilder();
        for (String path : nested.keySet()) {
            jars.append(jars.isEmpty() ? "" : ",")
                    .append("{\"file\": \"")
                    .append(path)
                    .append("\"}");
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "fabric.mod.json",
                ("{\"schemaVersion\": 1, \"id\": \"" + id + "\", \"version\": \"" + version + "\", \"jars\": [" + jars
                                + "]}")
                        .getBytes(StandardCharsets.UTF_8));
        entries.putAll(nested);
        return jar(entries);
    }

    private static byte[] quiltMod(String id, String version, Map<String, byte[]> nested, String... extraJars)
            throws IOException {
        StringBuilder jars = new StringBuilder();
        for (String path : nested.keySet()) {
            jars.append(jars.isEmpty() ? "" : ",").append('"').append(path).append('"');
        }
        for (String path : extraJars) {
            jars.append(jars.isEmpty() ? "" : ",").append('"').append(path).append('"');
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "quilt.mod.json",
                ("{\"schema_version\": 1, \"quilt_loader\": {\"id\": \"" + id + "\", \"version\": \"" + version
                                + "\", \"jars\": [" + jars + "]}}")
                        .getBytes(StandardCharsets.UTF_8));
        entries.putAll(nested);
        return jar(entries);
    }

    private ForeignMod topLevel(String name, byte[] content) throws IOException {
        Path path = directory.resolve(name);
        Files.write(path, content);
        return bridges.assess(ModFileScanner.classify(path), GameSide.CLIENT).orElseThrow();
    }

    private NestedJarExtractor extractor() {
        return new NestedJarExtractor(bridges, directory.resolve("cache"), GameSide.CLIENT);
    }

    private static List<String> codes(ForeignMod mod) {
        return mod.assessment().findings().stream()
                .map(CompatibilityFinding::code)
                .toList();
    }

    @Test
    void nestedJarsBecomeModsOfTheirOwnRecursively() throws IOException {
        byte[] deep = fabricMod("deep", "3.0.0", Map.of());
        byte[] lib = fabricMod("lib", "2.0.0", Map.of("META-INF/jars/deep.jar", deep));
        ForeignMod bundle = topLevel("bundle.jar", fabricMod("bundle", "1.0.0", Map.of("META-INF/jars/lib.jar", lib)));

        List<ForeignMod> mods = extractor().expand(List.of(bundle)).mods();

        assertEquals(
                List.of("bundle", "lib", "deep"),
                mods.stream().map(mod -> mod.assessment().modId()).toList());
        assertFalse(mods.get(0).nested());
        assertEquals(bundle.jar(), mods.get(1).containerJar());
        assertEquals(mods.get(1).jar(), mods.get(2).containerJar());
        assertTrue(Files.isRegularFile(mods.get(2).jar()));
        assertEquals(List.of("PLEXUS-COMPAT-120"), codes(mods.get(1)));
        assertTrue(mods.get(1)
                .assessment()
                .findings()
                .getFirst()
                .detail()
                .contains("inside bundle.jar as META-INF/jars/lib.jar"));
        assertTrue(mods.get(1).assessment().status().loadable(), "a note does not block");
    }

    @Test
    void aMissingEntryBlocksTheContainer() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "fabric.mod.json",
                "{\"id\": \"broken\", \"version\": \"1\", \"jars\": [{\"file\": \"META-INF/jars/gone.jar\"}]}"
                        .getBytes(StandardCharsets.UTF_8));
        ForeignMod broken = topLevel("broken.jar", jar(entries));

        ForeignMod result = extractor().expand(List.of(broken)).mods().getFirst();

        assertEquals(List.of("PLEXUS-COMPAT-123"), codes(result));
        assertFalse(result.assessment().status().loadable());
    }

    @Test
    void aNestedJarThatIsNotAModIsReportedApart() throws IOException {
        byte[] library = jar(Map.of("org/example/Util.class", new byte[] {1, 2, 3}));
        ForeignMod bundle =
                topLevel("bundle.jar", fabricMod("bundle", "1.0.0", Map.of("META-INF/jars/util.jar", library)));

        NestedJarExtractor.Result result = extractor().expand(List.of(bundle));

        assertEquals(1, result.mods().size());
        assertEquals(1, result.otherFiles().size());
        assertEquals("META-INF/jars/util.jar", result.otherFiles().getFirst().entry());
        assertEquals(ModFileKind.UNKNOWN, result.otherFiles().getFirst().kind());
    }

    @Test
    void theSameJarInTwoContainersIsExtractedOnce() throws IOException {
        byte[] lib = fabricMod("lib", "2.0.0", Map.of());
        ForeignMod first = topLevel("a.jar", fabricMod("a", "1.0.0", Map.of("META-INF/jars/lib.jar", lib)));
        ForeignMod second = topLevel("b.jar", fabricMod("b", "1.0.0", Map.of("META-INF/jars/lib.jar", lib)));

        List<ForeignMod> mods = extractor().expand(List.of(first, second)).mods();

        assertEquals(4, mods.size());
        assertEquals(mods.get(1).jar(), mods.get(3).jar(), "same content, same cache file");
        assertEquals(first.jar(), mods.get(1).containerJar());
        assertEquals(second.jar(), mods.get(3).containerJar());
    }

    @Test
    void aQuiltModNestsQuiltAndFabricModsAndTheirOwnBridgesAssessThem() throws IOException {
        Map<String, byte[]> nested = new LinkedHashMap<>();
        nested.put("META-INF/jars/quilt-lib.jar", quiltMod("quilt_lib", "1.0.0", Map.of()));
        nested.put("META-INF/jars/fabric-lib.jar", fabricMod("fabric_lib", "2.0.0", Map.of()));
        ForeignMod outer = topLevel("outer.jar", quiltMod("outer", "1.0.0", nested));

        NestedJarExtractor.Result result = extractor().expand(List.of(outer));

        assertEquals(
                List.of("outer=Quilt", "quilt_lib=Quilt", "fabric_lib=Fabric"),
                result.mods().stream()
                        .map(mod ->
                                mod.assessment().modId() + "=" + mod.bridge().loaderName())
                        .toList());
        assertEquals(List.of(), result.otherFiles());
    }

    @Test
    void aFabricModDoesNotTreatANestedQuiltOnlyJarAsAMod() throws IOException {
        ForeignMod outer = topLevel(
                "outer.jar",
                fabricMod(
                        "outer",
                        "1.0.0",
                        Map.of("META-INF/jars/quilt-lib.jar", quiltMod("quilt_lib", "1.0.0", Map.of()))));

        NestedJarExtractor.Result result = extractor().expand(List.of(outer));

        assertEquals(1, result.mods().size());
        assertEquals(ModFileKind.QUILT, result.otherFiles().getFirst().kind());
    }

    @Test
    void aMissingEntryOfAQuiltModBlocksIt() throws IOException {
        ForeignMod outer = topLevel("outer.jar", quiltMod("outer", "1.0.0", Map.of(), "META-INF/jars/gone.jar"));

        List<ForeignMod> mods = extractor().expand(List.of(outer)).mods();

        assertEquals(1, mods.size());
        assertEquals(List.of("PLEXUS-COMPAT-123"), codes(mods.getFirst()));
        assertTrue(mods.getFirst().assessment().findings().getFirst().detail().contains("gone.jar"));
    }

    @Test
    void aJarWithFabricAndQuiltMetadataIsAssessedAsFabricWithANote() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("fabric.mod.json", "{\"id\": \"dual\", \"version\": \"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
        entries.put(
                "quilt.mod.json",
                "{\"schema_version\": 1, \"quilt_loader\": {\"id\": \"dual\", \"version\": \"2.0.0\"}}"
                        .getBytes(StandardCharsets.UTF_8));

        ForeignMod dual = topLevel("dual.jar", jar(entries));

        assertEquals("Fabric", dual.bridge().loaderName());
        assertEquals("1.0.0", dual.assessment().version());
        assertEquals(List.of("PLEXUS-COMPAT-310"), codes(dual));
        assertTrue(dual.assessment().status().loadable(), "a note does not block");
    }
}
