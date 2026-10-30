package dev.plexusmodloader.compat.quilt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.fabric.FabricBridge;
import dev.plexusmodloader.compat.fabric.loader.FabricLoaderRuntime;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quiltmc.loader.api.LanguageAdapter;
import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.entrypoint.EntrypointContainer;
import org.quiltmc.loader.api.entrypoint.EntrypointException;
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;

class QuiltLoaderRuntimeTest {

    @TempDir
    Path directory;

    private QuiltTestLaunch launch;

    @BeforeEach
    void attach() throws IOException {
        QuiltTestLaunch.CALLS.clear();
        launch = new QuiltTestLaunch(directory, GameSide.DEDICATED_SERVER);
        FabricLoaderRuntime fabric =
                FabricLoaderRuntime.create(launch.context, "0.19.5", Map.of("mixinextras", "0.5.5"));
        FabricLoaderImpl.INSTANCE.attachPolyModLoaderRuntime(fabric);
        QuiltLoaderImpl.INSTANCE.attachPolyModLoaderRuntime(
                QuiltLoaderRuntime.create(launch.context, fabric, "0.31.0-beta.4"));
    }

    private static ModContainer mod(String id) {
        return QuiltLoader.getModContainer(id).orElseThrow(() -> new AssertionError(id + " is not visible"));
    }

    @Test
    void everyLoadedModOfEveryLoaderIsVisibleWithItsSourceTypeAndGroup() {
        assertEquals(
                ModContainer.BasicSourceType.NORMAL_QUILT, mod("quilt_hello").getSourceType());
        assertEquals("dev.example", mod("quilt_hello").metadata().group());
        assertEquals(
                ModContainer.BasicSourceType.NORMAL_FABRIC, mod("fabric_lib").getSourceType());
        assertEquals("loader.fabric", mod("fabric_lib").metadata().group());
        assertEquals(
                ModContainer.BasicSourceType.NORMAL_FABRIC, mod("fabric_inner").getSourceType());
        assertEquals(ModContainer.BasicSourceType.OTHER, mod("native_lib").getSourceType());
        assertEquals("loader.poly", mod("native_lib").metadata().group());
        assertEquals(ModContainer.BasicSourceType.OTHER, mod("forge_mod").getSourceType());
        assertEquals("loader.minecraftforge", mod("forge_mod").metadata().group());
        for (String builtIn : List.of("minecraft", "java", "quilt_loader", "mixinextras")) {
            assertEquals(ModContainer.BasicSourceType.BUILTIN, mod(builtIn).getSourceType(), builtIn);
            assertEquals("builtin", mod(builtIn).metadata().group(), builtIn);
        }
        assertEquals("0.31.0-beta.4", mod("quilt_loader").metadata().version().raw());
        assertSame(mod("quilt_loader"), mod("fabricloader"), "Quilt Loader provides fabricloader");
        assertEquals("26.2", mod("minecraft").metadata().version().raw());
        // quilt_loader, minecraft, java, mixinextras and the five loaded mods; fabricloader is quilt_loader's.
        assertEquals(9, QuiltLoader.getAllMods().size());
    }

    @Test
    void providedIdsFindTheModThatProvidesThem() {
        assertTrue(QuiltLoader.isModLoaded("quilt_hello_api"));
        assertSame(mod("quilt_hello"), mod("quilt_hello_versioned"));
        assertFalse(QuiltLoader.isModLoaded("absent_a"));
    }

    @Test
    void sourcePathsNameTheJarAndForANestedModTheEntryInsideItsContainer() {
        assertEquals(List.of(List.of(launch.quiltJar)), mod("quilt_hello").getSourcePaths());
        List<List<Path>> nested = mod("fabric_inner").getSourcePaths();
        assertEquals(1, nested.size());
        assertEquals(launch.fabricJar, nested.getFirst().getFirst());
        assertEquals("/META-INF/jars/fabric-inner.jar", nested.getFirst().get(1).toString());
        assertSame(launch.context.gameClassLoader(), mod("quilt_hello").getClassLoader());
    }

    @Test
    void filesInsideAModAreFoundThroughItsRootPathAlsoForModsTheHostRuns() throws IOException {
        assertEquals("hi", Files.readString(mod("quilt_hello").getPath("assets/quilt_hello/hello.txt")));
        assertTrue(Files.exists(mod("forge_mod").rootPath().resolve("META-INF/mods.toml")));
        assertTrue(Files.isDirectory(mod("java").rootPath()), "a built-in mod without a jar has an empty root");
    }

    @Test
    void aClassFindsTheModWhoseJarDefinesIt() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {launch.quiltJar.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> marker = Class.forName(QuiltTestLaunch.MARKER, false, loader);

            assertSame(mod("quilt_hello"), QuiltLoader.getModContainer(marker).orElseThrow());
        }
        assertTrue(QuiltLoader.getModContainer(String.class).isEmpty(), "a JDK class belongs to no mod");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void entrypointsAreSharedWithTheFabricLoaderApi() {
        List<Supplier> quilt = QuiltLoader.getEntrypoints("quilt_hello:greeting", Supplier.class);
        List<Supplier> fabric = FabricLoader.getInstance().getEntrypoints("quilt_hello:greeting", Supplier.class);

        assertEquals(1, quilt.size());
        assertEquals("hello", quilt.getFirst().get());
        assertSame(fabric.getFirst(), quilt.getFirst(), "one instance per mod and declaration, as in Quilt Loader");
        EntrypointContainer<Supplier> container = QuiltLoader.getEntrypointContainers(
                        "quilt_hello:greeting", Supplier.class)
                .getFirst();
        assertSame(mod("quilt_hello"), container.getProvider());
        assertEquals(QuiltTestLaunch.Greeting.class.getName(), container.getDefinition());
        assertEquals(List.of(), QuiltLoader.getEntrypoints("nobody:declares", Runnable.class));
        EntrypointException wrongType = assertThrows(
                EntrypointException.class, () -> QuiltLoader.getEntrypoints("quilt_hello:greeting", Runnable.class));
        assertEquals("quilt_hello:greeting", wrongType.getKey());
        assertTrue(QuiltLoaderImpl.INSTANCE.hasEntrypoints("main"));
        assertFalse(QuiltLoaderImpl.INSTANCE.hasEntrypoints("nobody:declares"));
    }

    @Test
    void theFabricLoaderApiSeesTheQuiltModsMainEntrypoint() {
        assertEquals(
                1,
                FabricLoader.getInstance()
                        .getEntrypoints("main", net.fabricmc.api.ModInitializer.class)
                        .size());
    }

    /**
     * Quilt Loader's {@code invokePreLaunch}: Quilt {@code pre_launch} first, then {@code preLaunch} of every mod. A
     * Fabric mod's {@code pre_launch} is not called (Fabric Loader does not know the key).
     */
    @Test
    void preLaunchRunsQuiltPreLaunchFirstThenFabricPreLaunch() {
        FabricBridge bridge = new FabricBridge();
        bridge.prepareLaunch(launch.context);

        bridge.beforeGame(launch.context);

        assertEquals(List.of("pre_launch quilt_hello", "preLaunch", "preLaunch"), QuiltTestLaunch.CALLS);
    }

    @Test
    void theObjectShareIsFabrics() {
        assertSame(FabricLoader.getInstance().getObjectShare(), QuiltLoader.getObjectShare());
    }

    @Test
    void directoriesFollowQuiltLoadersProperties() {
        Path game = directory.resolve("game").toAbsolutePath();
        assertEquals(game, QuiltLoader.getGameDir());
        assertEquals(game.resolve("config"), QuiltLoader.getConfigDir());
        assertTrue(Files.isDirectory(QuiltLoader.getConfigDir()));
        assertEquals(game.resolve(".cache"), QuiltLoader.getCacheDir());
        String previous = System.getProperty("loader.cacheDir");
        System.setProperty("loader.cacheDir", "other-cache");
        try {
            assertEquals(game.resolve("other-cache"), QuiltLoader.getCacheDir());
            assertTrue(Files.isDirectory(game.resolve("other-cache")));
        } finally {
            if (previous == null) {
                System.clearProperty("loader.cacheDir");
            } else {
                System.setProperty("loader.cacheDir", previous);
            }
        }
    }

    @Test
    void launchArgumentsLeaveTheAccessTokenOutWhenSanitized() {
        assertArrayEquals(new String[] {"--universe", "u"}, QuiltLoader.getLaunchArguments(true));
        assertEquals(4, QuiltLoader.getLaunchArguments(false).length);
    }

    @Test
    void gameAndEnvironment() {
        assertEquals("26.2", QuiltLoader.getRawGameVersion());
        assertEquals("26.2", QuiltLoader.getNormalizedGameVersion());
        assertEquals(EnvType.SERVER, MinecraftQuiltLoader.getEnvironmentType());
        assertFalse(QuiltLoader.isDevelopmentEnvironment());
    }

    /** 26.x is not obfuscated: the {@code official} namespace, every name maps to itself. */
    @Test
    void theMappingResolverIsTheOfficialNamespace() {
        MappingResolver resolver = QuiltLoader.getMappingResolver();

        assertEquals("official", resolver.getCurrentRuntimeNamespace());
        assertEquals(List.of("official"), List.copyOf(resolver.getNamespaces()));
        assertEquals("net.minecraft.world.Pos", resolver.mapClassName("intermediary", "net.minecraft.world.Pos"));
        assertEquals("tick", resolver.mapMethodName("official", "net.minecraft.world.Pos", "tick", "()V"));
        assertThrows(IllegalArgumentException.class, () -> resolver.mapClassName("official", "net/minecraft/A"));
    }

    @Test
    void theModTableListsEveryMod() {
        String table = QuiltLoader.createModTable();

        for (String id : List.of("quilt_hello", "fabric_lib", "native_lib", "forge_mod", "quilt_loader")) {
            assertTrue(table.contains(id), table);
        }
    }

    @Test
    void theDefaultLanguageAdapterCreatesClassesFieldsAndMethods() throws Exception {
        Supplier<?> created = LanguageAdapter.getDefault()
                .create(mod("quilt_hello"), QuiltTestLaunch.Greeting.class.getName(), Supplier.class);
        Supplier<?> field = LanguageAdapter.getDefault()
                .create(mod("quilt_hello"), QuiltTestLaunch.Greeting.class.getName() + "::INSTANCE", Supplier.class);

        assertEquals("hello", created.get());
        assertSame(QuiltTestLaunch.Greeting.INSTANCE, field);
        assertThrows(
                org.quiltmc.loader.api.LanguageAdapterException.class,
                () -> LanguageAdapter.getDefault().create(mod("quilt_hello"), "a.b::c::d", Supplier.class));
    }

    @Test
    void usedBeforeTheBridgeAttachesItThrowsQuiltsMessage() {
        QuiltLoaderRuntime attached = QuiltLoaderImpl.INSTANCE.polyModLoaderRuntime();
        QuiltLoaderImpl.INSTANCE.attachPolyModLoaderRuntime(null);
        try {
            IllegalStateException tooEarly = assertThrows(IllegalStateException.class, QuiltLoader::getAllMods);
            assertEquals("Accessed QuiltLoader too early!", tooEarly.getMessage());
        } finally {
            QuiltLoaderImpl.INSTANCE.attachPolyModLoaderRuntime(attached);
        }
    }
}
