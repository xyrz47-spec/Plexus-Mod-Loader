package dev.plexusmodloader.compat.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.common.collect.ImmutableMap;
import dev.plexusmodloader.api.runtime.GameSide;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The getBuilder() that {@link FabricClassTransformer} adds to {@code AttributeSupplier$Builder} under Forge and NeoForge
 * calls {@code AttributeBuilderBridge.wrap}, whose result is a Guava {@code ImmutableMap.Builder}. The game class
 * loader holds the host's Guava, so a bridge from Poly Mod Loader's own loader cannot link to the game's code: the JVM
 * refused the call with a loader constraint violation on {@code ImmutableMap$Builder} (corpusFabricForge 2026-10-27,
 * fabric-host-patch-probe). The transformer gives the game class loader its own copy of the bridge instead.
 */
class AttributeBuilderBridgeCopyTest {

    private static final String BRIDGE = "dev.plexusmodloader.compat.fabric.mixin.AttributeBuilderBridge";

    @Test
    void theGameClassLoaderGetsItsOwnBridgeLinkedToItsGuava() throws Exception {
        FabricClassTransformer transformer = new FabricClassTransformer(
                null,
                new EnvironmentStripper(GameSide.DEDICATED_SERVER, Set.of()),
                InterfaceInjector.of(List.of()),
                EnumExtender.of(List.of()),
                Optional.empty(),
                Optional.empty());
        URL guavaJar = ImmutableMap.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader gameGuava =
                new URLClassLoader(new URL[] {guavaJar}, ClassLoader.getPlatformClassLoader())) {
            ClassLoader game = new GeneratingLoader(transformer, gameGuava);

            Class<?> bridge = Class.forName(BRIDGE, true, game);
            Map<String, Integer> target = new HashMap<>();
            Object builder = bridge.getMethod("wrap", Map.class).invoke(null, target);
            Class<?> gameBuilder = Class.forName("com.google.common.collect.ImmutableMap$Builder", false, gameGuava);
            gameBuilder.getMethod("putAll", Map.class).invoke(builder, Map.of("alpha", 1));

            assertSame(game, bridge.getClassLoader());
            assertSame(game, builder.getClass().getClassLoader());
            assertSame(gameBuilder, builder.getClass().getSuperclass());
            assertNotSame(ImmutableMap.Builder.class, gameBuilder);
            assertEquals(Map.of("alpha", 1), target);
        }
    }

    /** Defines the classes the transformer generates, as the game class loader does before asking its parent. */
    private static final class GeneratingLoader extends ClassLoader {
        private final FabricClassTransformer transformer;

        GeneratingLoader(FabricClassTransformer transformer, ClassLoader parent) {
            super(parent);
            this.transformer = transformer;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = transformer.generate(name);
                    loaded = bytes == null ? super.loadClass(name, false) : defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
