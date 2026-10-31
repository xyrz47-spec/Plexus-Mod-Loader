package dev.plexusmodloader.compat.fabric.loader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricLoaderRuntimeTest {

    /** Created by the default language adapter in {@link #entrypointsOfAnyKeyAreCreatedOncePerDeclaration}. */
    public static final class Greeting implements Supplier<String> {
        @Override
        public String get() {
            return "hello";
        }
    }

    @TempDir
    Path directory;

    private FabricLoaderRuntime runtime;

    private Path jar(String name, String fabricModJson) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(path);
                JarOutputStream jar = new JarOutputStream(file)) {
            if (fabricModJson != null) {
                jar.putNextEntry(new JarEntry("fabric.mod.json"));
                jar.write(fabricModJson.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
            jar.putNextEntry(new JarEntry("assets/lib/hello.txt"));
            jar.write("hi".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return path;
    }

    @BeforeEach
    void build() throws IOException {
        Path lib = jar("lib.jar", """
                {"schemaVersion": 1, "id": "lib", "version": "1.2.0", "name": "Library", "provides": ["lib_alias"],
                 "authors": ["Ana", {"name": "Bo", "contact": {"email": "bo@example.com"}}],
                 "license": "MIT", "icon": {"16": "small.png", "64": "big.png"},
                 "custom": {"lib:settings": {"answer": 42, "tags": ["a", "b"]}},
                 "depends": {"minecraft": "~26.2"},
                 "entrypoints": {"lib:greeting": ["%s"]}}
                """.formatted(Greeting.class.getName()));
        Path inner = jar("inner.jar", "{\"schemaVersion\": 1, \"id\": \"inner\", \"version\": \"3.0.0\"}");
        Path nativeJar = jar("native.jar", null);
        runtime = FabricLoaderRuntime.create(
                new BridgeLaunchContext(
                        GameSide.DEDICATED_SERVER,
                        "26.2",
                        directory.resolve("game.jar"),
                        directory.resolve("game"),
                        List.of("--universe", "u", "--accessToken", "secret"),
                        getClass().getClassLoader(),
                        List.of(
                                new BridgeLaunchContext.LoadedMod(
                                        "Fabric",
                                        "inner",
                                        "3.0.0",
                                        "inner",
                                        List.of(),
                                        inner,
                                        lib,
                                        "META-INF/jars/inner.jar"),
                                new BridgeLaunchContext.LoadedMod(
                                        "Fabric", "lib", "1.2.0", "lib", List.of("lib_alias"), lib, null, null),
                                new BridgeLaunchContext.LoadedMod(
                                        "Plexus Mod Loader",
                                        "core",
                                        "2.1.0",
                                        "Core",
                                        List.of(),
                                        nativeJar,
                                        null,
                                        null))),
                "0.19.5");
        FabricLoaderImpl.INSTANCE.attachPlexusModLoaderRuntime(runtime);
    }

    @Test
    void everyLoadedModOfEveryLoaderAndTheBuiltInsAreVisible() {
        FabricLoader loader = FabricLoader.getInstance();

        assertTrue(loader.isModLoaded("lib"));
        assertTrue(loader.isModLoaded("lib_alias"));
        assertTrue(loader.isModLoaded("core"), "a Plexus Mod is visible to Fabric mods");
        assertTrue(loader.isModLoaded("minecraft"));
        assertTrue(loader.isModLoaded("fabricloader"));
        assertFalse(loader.isModLoaded("fabric-api"));
        assertEquals(
                "plexus",
                loader.getModContainer("core").orElseThrow().getMetadata().getType());
        assertEquals(
                "26.2",
                loader.getModContainer("minecraft")
                        .orElseThrow()
                        .getMetadata()
                        .getVersion()
                        .getFriendlyString());
        assertEquals(6, loader.getAllMods().size());
        assertEquals(EnvType.SERVER, loader.getEnvironmentType());
        assertEquals("26.2", loader.getRawGameVersion());
    }

    @Test
    void fabricModMetadataIsReadLikeFabric() {
        ModMetadata metadata =
                FabricLoader.getInstance().getModContainer("lib").orElseThrow().getMetadata();

        assertEquals("fabric", metadata.getType());
        assertEquals("Library", metadata.getName());
        assertEquals("1.2.0", metadata.getVersion().getFriendlyString());
        assertEquals(
                List.of("Ana", "Bo"),
                metadata.getAuthors().stream().map(person -> person.getName()).toList());
        assertEquals(
                "bo@example.com",
                metadata.getAuthors().stream()
                        .toList()
                        .get(1)
                        .getContact()
                        .get("email")
                        .orElseThrow());
        assertEquals(List.of("MIT"), List.copyOf(metadata.getLicense()));
        assertEquals("small.png", metadata.getIconPath(10).orElseThrow());
        assertEquals("big.png", metadata.getIconPath(32).orElseThrow());
        assertEquals("big.png", metadata.getIconPath(512).orElseThrow());
        assertEquals(
                42,
                metadata.getCustomValue("lib:settings")
                        .getAsObject()
                        .get("answer")
                        .getAsNumber()
                        .intValue());
        assertEquals(
                "b",
                metadata.getCustomValue("lib:settings")
                        .getAsObject()
                        .get("tags")
                        .getAsArray()
                        .get(1)
                        .getAsString());
        assertThrows(
                ClassCastException.class,
                () -> metadata.getCustomValue("lib:settings").getAsString());
        assertEquals(1, metadata.getDependencies().size());
    }

    @Test
    void nestedModsKnowTheirContainer() {
        ModContainer inner = FabricLoader.getInstance().getModContainer("inner").orElseThrow();
        ModContainer lib = FabricLoader.getInstance().getModContainer("lib").orElseThrow();

        assertEquals(ModOrigin.Kind.NESTED, inner.getOrigin().getKind());
        assertEquals("lib", inner.getOrigin().getParentModId());
        assertEquals("META-INF/jars/inner.jar", inner.getOrigin().getParentSubLocation());
        assertSame(lib, inner.getContainingMod().orElseThrow());
        assertEquals(List.of(inner), List.copyOf(lib.getContainedMods()));
        assertEquals(ModOrigin.Kind.PATH, lib.getOrigin().getKind());
    }

    @Test
    void filesInsideAModAreFoundThroughItsRootPath() throws IOException {
        Path hello = FabricLoader.getInstance()
                .getModContainer("lib")
                .orElseThrow()
                .findPath("assets/lib/hello.txt")
                .orElseThrow();

        assertEquals("hi", Files.readString(hello));
    }

    @Test
    void entrypointsOfAnyKeyAreCreatedOncePerDeclaration() {
        @SuppressWarnings("rawtypes")
        List<Supplier> first = FabricLoader.getInstance().getEntrypoints("lib:greeting", Supplier.class);
        @SuppressWarnings("rawtypes")
        List<Supplier> second = FabricLoader.getInstance().getEntrypoints("lib:greeting", Supplier.class);

        assertEquals(1, first.size());
        assertEquals("hello", first.getFirst().get());
        assertSame(first.getFirst(), second.getFirst());
        assertEquals(List.of(), FabricLoader.getInstance().getEntrypoints("nobody:declares", Runnable.class));
        assertThrows(
                EntrypointException.class,
                () -> FabricLoader.getInstance().getEntrypoints("lib:greeting", Runnable.class),
                "a Greeting is not a Runnable");
    }

    @Test
    void sharedObjectsAndListenersWorkLikeFabric() {
        AtomicReference<Object> seen = new AtomicReference<>();
        FabricLoader.getInstance().getObjectShare().whenAvailable("lib:answer", (key, value) -> seen.set(value));
        FabricLoader.getInstance().getObjectShare().put("lib:answer", 42);

        assertEquals(42, seen.get());
        assertEquals(42, FabricLoader.getInstance().getObjectShare().get("lib:answer"));
        assertThrows(
                IllegalArgumentException.class,
                () -> FabricLoader.getInstance().getObjectShare().get("nocolon"));
    }

    @Test
    void directoriesAndArgumentsComeFromTheLaunch() {
        FabricLoader loader = FabricLoader.getInstance();

        assertEquals(directory.resolve("game").toAbsolutePath(), loader.getGameDir());
        assertTrue(Files.isDirectory(loader.getConfigDir()));
        assertArrayEquals(new String[] {"--universe", "u"}, loader.getLaunchArguments(true));
        assertEquals(4, loader.getLaunchArguments(false).length);
    }

    /** 26.x is not obfuscated: Fabric Loader's resolver without mappings, every name maps to itself. */
    @Test
    void theMappingResolverIsTheOfficialNamespace() {
        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();

        assertEquals("official", resolver.getCurrentRuntimeNamespace());
        assertEquals("net.minecraft.world.Pos", resolver.mapClassName("intermediary", "net.minecraft.world.Pos"));
        assertEquals("tick", resolver.mapMethodName("official", "net.minecraft.world.Pos", "tick", "()V"));
        assertThrows(IllegalArgumentException.class, () -> resolver.mapClassName("official", "net/minecraft/A"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void gameInstanceComesFromTheLaunch(@TempDir Path directory) {
        Object[] game = {null};
        FabricLoaderImpl.INSTANCE.attachPlexusModLoaderRuntime(FabricLoaderRuntime.create(
                new BridgeLaunchContext(
                        GameSide.CLIENT,
                        "26.2",
                        directory.resolve("game.jar"),
                        directory,
                        List.of(),
                        getClass().getClassLoader(),
                        List.of(),
                        () -> game[0]),
                "0.19.5"));

        assertNull(FabricLoader.getInstance().getGameInstance(), "null before the game object exists, as in Fabric");
        game[0] = "the Minecraft object";
        assertEquals("the Minecraft object", FabricLoader.getInstance().getGameInstance());
    }

    /** ADR-0025: a Forge mod in the same game, run by the host loader, as Fabric mods see it. */
    @Test
    void modsTheHostRunsAreVisibleWithoutRootPaths(@TempDir Path directory) throws IOException {
        Path forgeJar = directory.resolve("forge-mod.jar");
        try (OutputStream file = Files.newOutputStream(forgeJar);
                JarOutputStream jar = new JarOutputStream(file)) {
            jar.putNextEntry(new JarEntry("assets/forge_mod/lang/en_us.json"));
            jar.write("{}".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        FabricLoaderImpl.INSTANCE.attachPlexusModLoaderRuntime(FabricLoaderRuntime.create(
                new BridgeLaunchContext(
                        GameSide.DEDICATED_SERVER,
                        "26.2",
                        directory.resolve("game.jar"),
                        directory,
                        List.of(),
                        getClass().getClassLoader(),
                        List.of(
                                new BridgeLaunchContext.LoadedMod(
                                        "MinecraftForge",
                                        "forge_mod",
                                        "2.0.0",
                                        "Forge Mod",
                                        List.of(),
                                        forgeJar,
                                        null,
                                        null,
                                        true),
                                new BridgeLaunchContext.LoadedMod(
                                        "MinecraftForge",
                                        "forge_inner",
                                        "1.1.0",
                                        "Forge Inner",
                                        List.of(),
                                        forgeJar,
                                        forgeJar,
                                        "META-INF/jarjar/inner.jar",
                                        true))),
                "0.19.5"));
        FabricLoader loader = FabricLoader.getInstance();

        assertTrue(loader.isModLoaded("forge_mod"));
        ModContainer forgeMod = loader.getModContainer("forge_mod").orElseThrow();
        assertEquals("minecraftforge", forgeMod.getMetadata().getType());
        assertEquals("2.0.0", forgeMod.getMetadata().getVersion().getFriendlyString());
        assertEquals("Forge Mod", forgeMod.getMetadata().getName());
        assertEquals(
                List.of(),
                forgeMod.getRootPaths(),
                "Forge serves its resources; Fabric API must not make a second pack");
        assertEquals(ModOrigin.Kind.PATH, forgeMod.getOrigin().getKind());
        assertEquals(List.of(forgeJar), forgeMod.getOrigin().getPaths());
        ModContainer inner = loader.getModContainer("forge_inner").orElseThrow();
        assertEquals(ModOrigin.Kind.NESTED, inner.getOrigin().getKind());
        assertEquals("forge_mod", inner.getOrigin().getParentModId());
        assertSame(forgeMod, inner.getContainingMod().orElseThrow());
    }
}
