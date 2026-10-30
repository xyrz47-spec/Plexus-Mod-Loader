package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-0021 G8: Fabric and Quilt mods register while Forge's registries are opened, and the registries are closed again
 * afterwards. The classes under test have the names and the members {@code javap -c} shows in Forge 26.1-62.0.9,
 * 26.1.2-64.1.5, 26.2-65.1.5 and 26.3-66.1.0 (the same in all four): {@code ForgeRegistry.isLocked}/{@code
 * unfreeze}/{@code freeze} (which set {@code NamespacedWrapper.locked}) and {@code getWrapper}; {@code
 * NamespacedWrapper.register} checks {@code locked}, its {@code validateWrite} (from {@code createIntrusiveHolder})
 * checks its own {@code frozen}; Forge's patched {@code MappedRegistry} checks {@code frozen} in its private {@code
 * validateWrite} and has {@code unfreeze()}. Vanilla's {@code BuiltInRegistries.freeze()} freezes both before the mod
 * initialization point unless Fabric API's registry sync holds it. Minecraft types are left out (strings stand for keys
 * and values).
 */
class ForgeRegistryOpeningTest {

    private static final String FORGE = "net.minecraftforge.registries";

    private static final String REGISTRY_MANAGER = """
            package net.minecraftforge.registries;

            import net.minecraft.core.MappedRegistry;
            import net.minecraft.core.registries.BuiltInRegistries;

            public class RegistryManager {
                public static final RegistryManager ACTIVE = new RegistryManager();
                final java.util.Map<String, ForgeRegistry<?>> registries = new java.util.LinkedHashMap<>();

                public java.util.Map<String, ? extends ForgeRegistry<?>> getRegistries() {
                    return registries;
                }

                /** Test fixture: a registry Forge wraps, its wrapper frozen or not. */
                public static NamespacedWrapper<Object> add(String name, boolean forgeFrozen, boolean wrapperFrozen) {
                    ForgeRegistry<Object> registry = new ForgeRegistry<>(name);
                    if (forgeFrozen) {
                        registry.freeze();
                    }
                    if (wrapperFrozen) {
                        registry.getWrapper().freeze();
                    }
                    ACTIVE.registries.put(name, registry);
                    BuiltInRegistries.REGISTRY.values.add(registry.getWrapper());
                    return registry.getWrapper();
                }

                /** Test fixture: a game registry Forge does not wrap, frozen or not. */
                public static MappedRegistry<Object> addVanilla(String name, boolean frozen) {
                    MappedRegistry<Object> registry = new MappedRegistry<>(name);
                    if (frozen) {
                        registry.freeze();
                    }
                    BuiltInRegistries.REGISTRY.values.add(registry);
                    return registry;
                }

                public static ForgeRegistry<?> registry(String name) {
                    return ACTIVE.registries.get(name);
                }
            }
            """;

    private static final String FORGE_REGISTRY = """
            package net.minecraftforge.registries;

            public class ForgeRegistry<V> {
                private boolean isFrozen;
                private final NamespacedWrapper<V> wrapper;

                ForgeRegistry(String name) {
                    wrapper = new NamespacedWrapper<>(name);
                }

                NamespacedWrapper<V> getWrapper() {
                    return wrapper;
                }

                public boolean isLocked() {
                    return isFrozen;
                }

                public void freeze() {
                    isFrozen = true;
                    NamespacedWrapper<V> w = getWrapper();
                    if (w != null) {
                        w.locked = true;
                    }
                }

                public void unfreeze() {
                    isFrozen = false;
                    NamespacedWrapper<V> w = getWrapper();
                    if (w != null) {
                        w.locked = false;
                    }
                }
            }
            """;

    /** {@code GUARD} is the field {@code validateWrite} checks, {@code frozen} in Forge; {@code IS_FROZEN} its getter. */
    private static final String NAMESPACED_WRAPPER = """
            package net.minecraftforge.registries;

            import net.minecraft.core.MappedRegistry;

            public class NamespacedWrapper<T> extends MappedRegistry<T> {
                boolean locked;
                private boolean frozen;
                private boolean sealed;

                NamespacedWrapper(String name) {
                    super(name);
                }

                @Override
                public Object register(String key, T value) {
                    if (locked) {
                        throw new IllegalStateException("Can not register to a locked registry.");
                    }
                    validateWrite(key);
                    values.add(value);
                    return value;
                }

                void validateWrite() {
                    if (GUARD) {
                        throw new IllegalStateException("Registry " + name + " is already frozen");
                    }
                }

                void validateWrite(String key) {
                    if (GUARD) {
                        throw new IllegalStateException("Registry " + name + " is already frozen (trying to add key " + key + ")");
                    }
                }

                public Object createIntrusiveHolder(T value) {
                    validateWrite();
                    return value;
                }

                @Override
                public MappedRegistry<T> freeze() {
                    if (frozen) {
                        return this;
                    }
                    frozen = true;
                    freezes++;
                    return this;
                }

                @Override
                public void unfreeze() {
                    frozen = false;
                }

                IS_FROZEN

                public void lock() {
                    locked = true;
                }

                public void seal() {
                    sealed = true;
                }
            }
            """;

    /** {@code GUARD} is the field the private {@code validateWrite} checks, {@code frozen} in Forge's patched game. */
    private static final String MAPPED_REGISTRY = """
            package net.minecraft.core;

            public class MappedRegistry<T> implements Iterable<T> {
                private boolean frozen;
                private boolean sealed;
                protected final String name;
                public final java.util.List<T> values = new java.util.ArrayList<>();
                public int freezes;

                public MappedRegistry(String name) {
                    this.name = name;
                }

                private void validateWrite(String key) {
                    if (GUARD) {
                        throw new IllegalStateException("Registry is already frozen (trying to add key " + key + ")");
                    }
                }

                public Object register(String key, T value) {
                    validateWrite(key);
                    values.add(value);
                    return value;
                }

                public MappedRegistry<T> freeze() {
                    if (frozen) {
                        return this;
                    }
                    frozen = true;
                    freezes++;
                    return this;
                }

                public void unfreeze() {
                    frozen = false;
                }

                public void seal() {
                    sealed = true;
                }

                @Override
                public java.util.Iterator<T> iterator() {
                    return values.iterator();
                }
            }
            """;

    private static final String BUILT_IN_REGISTRIES = """
            package net.minecraft.core.registries;

            import net.minecraft.core.MappedRegistry;

            public class BuiltInRegistries {
                public static final MappedRegistry<MappedRegistry<?>> REGISTRY = new MappedRegistry<>("minecraft:root");
            }
            """;

    private static final String IS_FROZEN = "boolean isFrozen() { return frozen; }";

    @TempDir
    Path temp;

    @Test
    void registriesFrozenByVanillaOpenForRegistrationAndFreezeAgain() throws Exception {
        // Forge 26.3-66.1.0 with Fabric API's registry sync left out (PLEXUS-COMPAT-224): vanilla's freeze has frozen
        // the
        // wrappers and the other registries, so Block.<init> -> createIntrusiveHolder -> validateWrite threw "Registry
        // minecraft:block is already frozen", and then a creative tab's MappedRegistry.register (PLEXUS-COMPAT-110).
        try (URLClassLoader game = forge("frozen", IS_FROZEN, "frozen")) {
            Object blocks = add(game, "minecraft:block", true, true);
            Object items = add(game, "minecraft:item", true, true);
            Object tabs = addVanilla(game, "minecraft:creative_mode_tab", true);

            ForgeRegistryOpening.run(game, () -> {
                call(blocks, "createIntrusiveHolder", "mta:block");
                call(blocks, "register", "mta:block", "mta:block");
                call(items, "register", "mta:item", "mta:item");
                call(tabs, "register", "mta:tab", "mta:tab");
            });

            assertEquals(List.of("mta:block"), values(blocks));
            assertEquals(List.of("mta:item"), values(items));
            assertEquals(List.of("mta:tab"), values(tabs));
            for (Object wrapper : List.of(blocks, items)) {
                assertTrue((boolean) call(wrapper, "isFrozen"), "the wrapper is frozen again");
                assertEquals(2, freezes(wrapper), "through the wrapper's freeze(), which binds the new entries' tags");
                assertTrue(field(wrapper, "locked"), "the wrapper is locked again");
            }
            assertTrue(field(tabs, "frozen"), "the game registry is frozen again");
            assertEquals(2, freezes(tabs), "through its freeze()");
            assertTrue((boolean) call(registry(game, "minecraft:block"), "isLocked"));
            assertThrows(WrappedException.class, () -> call(blocks, "createIntrusiveHolder", "late"));
            assertThrows(WrappedException.class, () -> call(blocks, "register", "late", "late"));
            assertThrows(WrappedException.class, () -> call(tabs, "register", "late", "late"));
        }
    }

    @Test
    void registriesLeftOpenByRegistrySyncStayOpen() throws Exception {
        // Forge 26.2-65.1.5 with Fabric API: registry sync holds vanilla's freeze until after mod initialization.
        try (URLClassLoader game = forge("frozen", IS_FROZEN, "frozen")) {
            Object blocks = add(game, "minecraft:block", true, false);
            Object tabs = addVanilla(game, "minecraft:creative_mode_tab", false);

            ForgeRegistryOpening.run(game, () -> {
                call(blocks, "register", "mod:block", "mod:block");
                call(tabs, "register", "mod:tab", "mod:tab");
            });

            assertEquals(List.of("mod:block"), values(blocks));
            assertEquals(List.of("mod:tab"), values(tabs));
            assertFalse((boolean) call(blocks, "isFrozen"), "registry sync freezes it later, as before");
            assertFalse(field(tabs, "frozen"));
            assertEquals(0, freezes(blocks));
            assertEquals(0, freezes(tabs));
            assertTrue(field(blocks, "locked"));
            assertTrue((boolean) call(registry(game, "minecraft:block"), "isLocked"));
        }
    }

    @Test
    void closesTheRegistriesWhenInitializationThrows() throws Exception {
        try (URLClassLoader game = forge("frozen", IS_FROZEN, "frozen")) {
            Object blocks = add(game, "minecraft:block", true, true);
            Object tabs = addVanilla(game, "minecraft:creative_mode_tab", true);

            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> ForgeRegistryOpening.run(game, () -> {
                        throw new IllegalStateException("mod failed");
                    }));

            assertEquals("mod failed", thrown.getMessage());
            assertTrue((boolean) call(blocks, "isFrozen"));
            assertTrue(field(blocks, "locked"));
            assertTrue(field(tabs, "frozen"));
            assertTrue((boolean) call(registry(game, "minecraft:block"), "isLocked"));
        }
    }

    @Test
    void wrapperWriteCheckOnAFieldPolyModLoaderDoesNotOpenStopsBeforeModsRun() throws Exception {
        try (URLClassLoader game = forge("sealed", IS_FROZEN, "frozen")) {
            Object blocks = add(game, "minecraft:block", true, true);
            AtomicBoolean ran = new AtomicBoolean();

            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class, () -> ForgeRegistryOpening.run(game, () -> ran.set(true)));

            assertTrue(thrown.getMessage().startsWith("PLEXUS-COMPAT-227 "), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("NamespacedWrapper's register/validateWrite check sealed"));
            assertFalse(ran.get(), "no mod code runs against registries that are not open");
            assertTrue((boolean) call(blocks, "isFrozen"), "nothing was changed");
            assertTrue(field(blocks, "locked"));
        }
    }

    @Test
    void gameRegistryWriteCheckOnAFieldPolyModLoaderDoesNotOpenStopsBeforeModsRun() throws Exception {
        try (URLClassLoader game = forge("frozen", IS_FROZEN, "sealed")) {
            Object tabs = addVanilla(game, "minecraft:creative_mode_tab", true);
            AtomicBoolean ran = new AtomicBoolean();

            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class, () -> ForgeRegistryOpening.run(game, () -> ran.set(true)));

            assertTrue(thrown.getMessage().startsWith("PLEXUS-COMPAT-227 "), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("MappedRegistry's register/validateWrite check sealed"));
            assertFalse(ran.get());
            assertTrue(field(tabs, "frozen"), "nothing was changed");
        }
    }

    @Test
    void missingMemberStopsBeforeModsRun() throws Exception {
        try (URLClassLoader game = forge("frozen", "", "frozen")) {
            add(game, "minecraft:block", true, true);
            AtomicBoolean ran = new AtomicBoolean();

            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class, () -> ForgeRegistryOpening.run(game, () -> ran.set(true)));

            assertTrue(thrown.getMessage().startsWith("PLEXUS-COMPAT-227 "), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("NamespacedWrapper.isFrozen()"), thrown.getMessage());
            assertFalse(ran.get());
        }
    }

    /**
     * Forge's own {@code NamespacedWrapper} passes the class-file check ({@code -PforgeUniversal=<forge-<version>-
     * universal.jar>}; checked with 26.1-62.0.9, 26.1.2-64.1.5, 26.2-65.1.5 and 26.3-66.1.0).
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.forgeUniversal", matches = ".+")
    void forgesNamespacedWrapperHasTheCheckedWriteChecks() throws IOException {
        ClassModel wrapper = classModel(
                System.getProperty("poly.forgeUniversal"), "net/minecraftforge/registries/NamespacedWrapper.class");
        ForgeRegistryOpening.checkWriteChecks(
                wrapper, Set.of(ForgeRegistryOpening.LOCKED, ForgeRegistryOpening.FROZEN), true);
    }

    /**
     * Forge's patched {@code MappedRegistry} passes the class-file check ({@code -PforgePatchedClient=<forge-<version>-
     * client.jar>} or {@code -PforgePatchedServer=<forge-<version>-server.jar>} from a Forge run's {@code .poly/forge/};
     * checked with the 26.1-62.0.9, 26.1.2-64.1.5 and 26.2-65.1.5 client jars and the 26.3-66.1.0 server jar).
     */
    @Test
    void forgesPatchedMappedRegistryHasTheCheckedWriteChecks() throws IOException {
        String jar = System.getProperty("poly.forgePatchedClient", System.getProperty("poly.forgePatchedServer", ""));
        assumeFalse(jar.isEmpty(), "no Forge patched game jar given");
        ClassModel mapped = classModel(jar, "net/minecraft/core/MappedRegistry.class");
        ForgeRegistryOpening.checkWriteChecks(mapped, Set.of(ForgeRegistryOpening.FROZEN), false);
    }

    private static ClassModel classModel(String jar, String entryName) throws IOException {
        try (JarFile file = new JarFile(jar)) {
            JarEntry entry = file.getJarEntry(entryName);
            assertNotNull(entry, jar + " has " + entryName);
            try (InputStream in = file.getInputStream(entry)) {
                return ClassFile.of().parse(in.readAllBytes());
            }
        }
    }

    /**
     * Compiles Forge's registry classes with the wrapper's {@code validateWrite} checking {@code wrapperGuard} and
     * {@code MappedRegistry}'s checking {@code gameGuard}.
     */
    private URLClassLoader forge(String wrapperGuard, String isFrozen, String gameGuard) throws IOException {
        Path sources = Files.createDirectories(temp.resolve("src"));
        Path classes = Files.createDirectories(temp.resolve("classes"));
        Map<String, String> files = Map.of(
                "RegistryManager", REGISTRY_MANAGER,
                "ForgeRegistry", FORGE_REGISTRY,
                "NamespacedWrapper",
                        NAMESPACED_WRAPPER.replace("GUARD", wrapperGuard).replace("IS_FROZEN", isFrozen),
                "MappedRegistry", MAPPED_REGISTRY.replace("GUARD", gameGuard),
                "BuiltInRegistries", BUILT_IN_REGISTRIES);
        List<String> arguments = new ArrayList<>(List.of("-d", classes.toString(), "-proc:none", "-nowarn"));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path source = sources.resolve(file.getKey() + ".java");
            Files.writeString(source, file.getValue());
            arguments.add(source.toString());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, arguments.toArray(String[]::new)), "fixture compiles");
        return new URLClassLoader(new URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static Object add(ClassLoader game, String name, boolean forgeFrozen, boolean wrapperFrozen)
            throws ReflectiveOperationException {
        return manager(game)
                .getMethod("add", String.class, boolean.class, boolean.class)
                .invoke(null, name, forgeFrozen, wrapperFrozen);
    }

    private static Object addVanilla(ClassLoader game, String name, boolean frozen)
            throws ReflectiveOperationException {
        return manager(game)
                .getMethod("addVanilla", String.class, boolean.class)
                .invoke(null, name, frozen);
    }

    private static Object registry(ClassLoader game, String name) throws ReflectiveOperationException {
        return manager(game).getMethod("registry", String.class).invoke(null, name);
    }

    private static Class<?> manager(ClassLoader game) throws ClassNotFoundException {
        return Class.forName(FORGE + ".RegistryManager", true, game);
    }

    private static List<?> values(Object registry) throws ReflectiveOperationException {
        return (List<?>) registry.getClass().getField("values").get(registry);
    }

    private static int freezes(Object registry) throws ReflectiveOperationException {
        return registry.getClass().getField("freezes").getInt(registry);
    }

    private static boolean field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    /** Calls {@code target.name(arguments)}, the only method of its class with that name and argument count. */
    private static Object call(Object target, String name, Object... arguments) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                method.setAccessible(true);
                try {
                    return method.invoke(target, arguments);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                } catch (InvocationTargetException e) {
                    throw new WrappedException(e);
                }
            }
        }
        throw new AssertionError("no " + name + " with " + arguments.length + " argument(s)");
    }

    /** What a fixture method threw, as an unchecked exception that keeps the original as its cause. */
    private static final class WrappedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        WrappedException(InvocationTargetException e) {
            super(e.getCause().toString(), e);
        }
    }
}
