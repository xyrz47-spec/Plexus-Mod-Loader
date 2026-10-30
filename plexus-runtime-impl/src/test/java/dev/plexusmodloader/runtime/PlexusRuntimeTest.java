package dev.plexusmodloader.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.game.version.GameLaunchTarget;
import dev.plexusmodloader.runtime.diagnostics.PlexusLaunchException;
import dev.plexusmodloader.runtime.diagnostics.StartupReport;
import dev.plexusmodloader.runtime.diagnostics.TransformationReport;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Runs the whole launch pipeline against a synthetic game jar and mod jar generated with ASM, so the
 * pipeline is tested without Minecraft: discovery, metadata, dependencies, transformation planning,
 * game class loading, entrypoints, and the call into the game's {@code main}.
 */
class PlexusRuntimeTest {

    private static final List<String> PROPERTIES =
            List.of("poly.test.brand", "poly.test.loader", "poly.test.mod", "poly.test.storage");

    @TempDir
    Path directory;

    @AfterEach
    void clearProperties() {
        PROPERTIES.forEach(System::clearProperty);
    }

    @Test
    void launchesTheGameThroughTheTransformingClassLoaderAfterRunningModEntrypoints() throws Throwable {
        Path gameJar = gameJar();
        Path modJar = modJar(">=1.0.0 <2.0.0", "test-1");

        try (LaunchedGame game = new PlexusRuntime().launch(configuration(gameJar, modJar), new String[0])) {
            assertEquals("test-1", game.gameVersion());
        }

        assertEquals("plexus", System.getProperty("poly.test.brand"), "game saw the default plexus:game_brand system");
        assertEquals(
                "poly-game",
                System.getProperty("poly.test.loader"),
                "game classes come from the Poly Mod Loader loader");
        assertEquals("test_mod", System.getProperty("poly.test.mod"), "mod entrypoint ran with its context");
    }

    @Test
    void aModReplacingTheBrandSystemChangesWhatTheGameSees() throws Throwable {
        Path brandMod = brandMod("brand_mod", false, List.of());

        try (LaunchedGame game = new PlexusRuntime().launch(configuration(gameJar(), brandMod), new String[0])) {
            assertEquals("brand_mod", game.systems().activeSystems().getFirst().providerModId());
        }

        assertEquals("test/brand", System.getProperty("poly.test.brand"), "game saw the replacement provider");
        StartupReport report = StartupReport.read(directory.resolve(".poly").resolve(StartupReport.FILE_NAME));
        assertEquals("brand_mod", report.systems().getFirst().providerMod());
        assertEquals("testbrand.Provider", report.systems().getFirst().provider());
        assertEquals("test:brand", report.acceptedTransformations().getFirst().ruleId());
    }

    @Test
    void twoModsReplacingTheSameSystemStopTheLaunchBeforeAnyModCodeRuns() throws Exception {
        Path first = brandMod("first_brand", false, List.of());
        Path second = brandMod("second_brand", false, List.of());

        PlexusLaunchException failure = assertThrows(
                PlexusLaunchException.class,
                () -> new PlexusRuntime().launch(configuration(gameJar(), first, second), new String[0]));

        assertEquals("CONFLICT_RESOLUTION", failure.phase());
        assertTrue(
                failure.problems()
                        .getFirst()
                        .startsWith(
                                "PLEXUS-SYSTEM-001 system=plexus:game_brand: mods [first_brand, " + "second_brand]"),
                failure.getMessage());
        assertNull(System.getProperty("poly.test.brand"), "the game must not start after a conflict");
    }

    @Test
    void aDependentModMayExplicitlyOverrideAnotherReplacement() throws Throwable {
        Path base = brandMod("base_brand", false, List.of());
        Path pack = brandMod("pack_brand", true, List.of("base_brand"));

        try (LaunchedGame game = new PlexusRuntime().launch(configuration(gameJar(), base, pack), new String[0])) {
            assertEquals("pack_brand", game.systems().activeSystems().getFirst().providerModId());
            assertEquals(
                    List.of("base_brand"),
                    game.systems().activeSystems().getFirst().overriddenModIds());
        }
    }

    @Test
    void modDeclaredTransformsAreAppliedAndCachedAcrossLaunches() throws Throwable {
        Path transformMod = transformMod("Anvil");

        try (LaunchedGame game = new PlexusRuntime().launch(configuration(gameJar(), transformMod), new String[0])) {
            game.shutdown().runShutdownTasks();
        }
        assertEquals("Anvil+mod", System.getProperty("poly.test.storage"), "the mod's rule changed game behaviour");
        TransformationReport first =
                TransformationReport.read(directory.resolve(".poly/" + TransformationReport.FILE_NAME));

        System.clearProperty("poly.test.storage");
        try (LaunchedGame game = new PlexusRuntime().launch(configuration(gameJar(), transformMod), new String[0])) {
            game.shutdown().runShutdownTasks();
        }
        assertEquals("Anvil+mod", System.getProperty("poly.test.storage"));
        TransformationReport second =
                TransformationReport.read(directory.resolve(".poly/" + TransformationReport.FILE_NAME));

        assertEquals(
                List.of("transform_mod:storage_name"),
                entry(first, "fakegame/StorageName").appliedRuleIds());
        assertTrue(!entry(first, "fakegame/StorageName").fromCache(), "first launch transforms");
        assertTrue(entry(second, "fakegame/StorageName").fromCache(), "second launch reuses the cache");
        assertEquals(
                entry(first, "fakegame/StorageName").sha256After(),
                entry(second, "fakegame/StorageName").sha256After());
    }

    @Test
    void aModTransformWithAWrongPreconditionAndAbortPolicyStopsTheLaunch() throws Exception {
        Path transformMod = transformMod("McRegion");

        PlexusLaunchException failure = assertThrows(
                PlexusLaunchException.class,
                () -> new PlexusRuntime().launch(configuration(gameJar(), transformMod), new String[0]));

        assertEquals("TRANSFORMATION_PLANNING", failure.phase());
        assertTrue(failure.problems().getFirst().startsWith("PLEXUS-TRANSFORM-003 rule=transform_mod:storage_name"));
        assertNull(System.getProperty("poly.test.storage"), "the game must not start");
    }

    private static dev.plexusmodloader.internal.transform.engine.TransformReportEntry entry(
            TransformationReport report, String className) {
        return report.transformedClasses().stream()
                .filter(entry -> entry.className().equals(className))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void stopsBeforeTheGameStartsWhenDependenciesAreNotMet() throws Exception {
        Path modJar = modJar(">=9.0.0", "test-1");

        PlexusLaunchException failure = assertThrows(
                PlexusLaunchException.class,
                () -> new PlexusRuntime().launch(configuration(gameJar(), modJar), new String[0]));

        assertEquals("DEPENDENCY_RESOLUTION", failure.phase());
        assertTrue(
                failure.problems().getFirst().startsWith("PLEXUS-DEPENDENCY-003 mod=test_mod"), failure.getMessage());
        assertNull(System.getProperty("poly.test.mod"), "no entrypoint may run after a failed phase");
        assertNull(System.getProperty("poly.test.brand"), "the game must not start after a failed phase");
    }

    @Test
    void reportsInvalidMetadataWithTheJarName() throws Exception {
        Path badMod =
                jar("bad.jar", Map.of("poly.mod.json", "{\"schemaVersion\": 1}".getBytes(StandardCharsets.UTF_8)));

        PlexusLaunchException failure = assertThrows(
                PlexusLaunchException.class,
                () -> new PlexusRuntime().launch(configuration(gameJar(), badMod), new String[0]));

        assertEquals("METADATA_VALIDATION", failure.phase());
        assertTrue(failure.problems().stream().allMatch(line -> line.startsWith("bad.jar PLEXUS-METADATA-002")));
    }

    @Test
    void refusesGameVersionsWithoutAnAdapter() throws Exception {
        Path unknownGame =
                jar("unknown.jar", Map.of("version.json", "{\"id\": \"0.0\"}".getBytes(StandardCharsets.UTF_8)));

        PlexusLaunchException failure = assertThrows(
                PlexusLaunchException.class,
                () -> new PlexusRuntime().launch(configuration(unknownGame), new String[0]));

        assertTrue(failure.problems().getFirst().startsWith("PLEXUS-BOOTSTRAP-002"), failure.getMessage());
    }

    private LaunchConfiguration configuration(Path gameJar, Path... devMods) throws IOException {
        Path mods = Files.createDirectories(directory.resolve("mods"));
        return new LaunchConfiguration(
                GameLaunchTarget.CLIENT,
                gameJar,
                mods,
                List.of(devMods),
                Optional.empty(),
                directory.resolve(".poly"),
                Optional.of(directory.resolve(".poly/transform-cache")));
    }

    private Path gameJar() throws IOException {
        return jar(
                "game.jar",
                Map.of(
                        "version.json", "{\"id\": \"test-1\"}".getBytes(StandardCharsets.UTF_8),
                        "fakegame/Brand.class", brandClass(),
                        "fakegame/Main.class", mainClass(),
                        "fakegame/StorageName.class", storageNameClass()));
    }

    private Path modJar(String polyRange, String minecraft) throws IOException {
        String metadata = """
                {
                  "schemaVersion": 1,
                  "id": "test_mod",
                  "version": "1.0.0",
                  "name": "Test Mod",
                  "environment": "both",
                  "minecraft": ["%s"],
                  "entrypoints": { "common": ["testmod.Init"] },
                  "depends": { "poly": "%s" }
                }
                """.formatted(minecraft, polyRange);
        return jar(
                "test-mod.jar",
                Map.of(
                        "poly.mod.json",
                        metadata.getBytes(StandardCharsets.UTF_8),
                        "testmod/Init.class",
                        entrypointClass()));
    }

    /** A mod declaring a transform of {@code fakegame/StorageName.name()} from {@code expected} to {@code Anvil+mod}. */
    private Path transformMod(String expected) throws IOException {
        String metadata = """
                {
                  "schemaVersion": 1,
                  "id": "transform_mod",
                  "version": "1.0.0",
                  "name": "Transform Mod",
                  "environment": "both",
                  "minecraft": ["test-1"],
                  "depends": { "poly": "*" },
                  "capabilities": ["internal.bytecode.transform"],
                  "transforms": [{
                    "id": "transform_mod:storage_name",
                    "minecraft": "test-1",
                    "sides": ["client", "dedicated_server"],
                    "target": {"owner": "fakegame/StorageName", "method": "name", "descriptor": "()Ljava/lang/String;"},
                    "action": {"type": "replace_returned_string_constant", "expected": "%s", "replacement": "Anvil+mod"},
                    "onFailure": "abort_startup"
                  }]
                }
                """.formatted(expected);
        return jar("transform-mod.jar", Map.of("poly.mod.json", metadata.getBytes(StandardCharsets.UTF_8)));
    }

    /** {@code public class StorageName { public static String name() { return "Anvil"; } }} */
    private static byte[] storageNameClass() {
        ClassWriter writer = newClass("fakegame/StorageName", null);
        MethodVisitor method =
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn("Anvil");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A mod whose provider class {@code testbrand.Provider} replaces {@code poly:game_brand} with {@code test/brand}. */
    private Path brandMod(String id, boolean override, List<String> depends) throws IOException {
        StringBuilder dependencies = new StringBuilder("\"poly\": \"*\"");
        depends.forEach(
                dependency -> dependencies.append(", \"").append(dependency).append("\": \"*\""));
        String metadata = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "1.0.0",
                  "name": "%s",
                  "environment": "both",
                  "minecraft": ["test-1"],
                  "depends": { %s },
                  "capabilities": ["system.provider.replace"],
                  "replacesSystems": [
                    { "system": "poly:game_brand", "provider": "testbrand.Provider", "override": %s }
                  ]
                }
                """.formatted(id, id, dependencies, override);
        return jar(
                id + ".jar",
                Map.of(
                        "poly.mod.json",
                        metadata.getBytes(StandardCharsets.UTF_8),
                        "testbrand/Provider.class",
                        brandProviderClass()));
    }

    /**
     * {@code public class Provider implements SystemProvider, GameBrandSystem}: {@code create} returns
     * {@code this}, {@code brand} returns {@code "test/brand"}.
     */
    private static byte[] brandProviderClass() {
        ClassWriter writer = newClass("testbrand/Provider", new String[] {
            "dev/plexusmodloader/api/system/SystemProvider", "dev/plexusmodloader/api/system/brand/GameBrandSystem"
        });
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor create = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "create",
                "(Ldev/plexusmodloader/api/system/SystemContext;)Ldev/plexusmodloader/api/system/PolySystem;",
                null,
                null);
        create.visitCode();
        create.visitVarInsn(Opcodes.ALOAD, 0);
        create.visitInsn(Opcodes.ARETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();
        MethodVisitor brand = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "brand",
                "(Ldev/plexusmodloader/api/runtime/GameSide;)Ljava/lang/String;",
                null,
                null);
        brand.visitCode();
        brand.visitLdcInsn("test/brand");
        brand.visitInsn(Opcodes.ARETURN);
        brand.visitMaxs(0, 0);
        brand.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream output = new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }

    /** {@code public class Brand { public static String brand() { return "vanilla"; } }} */
    private static byte[] brandClass() {
        ClassWriter writer = newClass("fakegame/Brand", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "brand", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn("vanilla");
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** {@code main} records the brand it sees and which class loader defined it. */
    private static byte[] mainClass() {
        ClassWriter writer = newClass("fakegame/Main", null);
        MethodVisitor main = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitCode();
        main.visitLdcInsn("poly.test.brand");
        main.visitMethodInsn(Opcodes.INVOKESTATIC, "fakegame/Brand", "brand", "()Ljava/lang/String;", false);
        setProperty(main);
        main.visitLdcInsn("poly.test.loader");
        main.visitLdcInsn(Type.getObjectType("fakegame/Main"));
        main.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
        main.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ClassLoader", "getName", "()Ljava/lang/String;", false);
        setProperty(main);
        main.visitLdcInsn("poly.test.storage");
        main.visitMethodInsn(Opcodes.INVOKESTATIC, "fakegame/StorageName", "name", "()Ljava/lang/String;", false);
        setProperty(main);
        main.visitInsn(Opcodes.RETURN);
        main.visitMaxs(0, 0);
        main.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** A {@code CommonModInitializer} that records {@code context.modId()}. */
    private static byte[] entrypointClass() {
        ClassWriter writer =
                newClass("testmod/Init", new String[] {"dev/plexusmodloader/api/lifecycle/CommonModInitializer"});
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor initialize = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "onInitializeCommon",
                "(Ldev/plexusmodloader/api/lifecycle/ModContext;)V",
                null,
                null);
        initialize.visitCode();
        initialize.visitLdcInsn("poly.test.mod");
        initialize.visitVarInsn(Opcodes.ALOAD, 1);
        initialize.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "dev/plexusmodloader/api/lifecycle/ModContext",
                "modId",
                "()Ljava/lang/String;",
                true);
        setProperty(initialize);
        initialize.visitInsn(Opcodes.RETURN);
        initialize.visitMaxs(0, 0);
        initialize.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter newClass(String name, String[] interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", interfaces);
        return writer;
    }

    private static void setProperty(MethodVisitor method) {
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false);
        method.visitInsn(Opcodes.POP);
    }
}
