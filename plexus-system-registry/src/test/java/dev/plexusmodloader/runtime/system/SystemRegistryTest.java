package dev.plexusmodloader.runtime.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.system.SystemContext;
import dev.plexusmodloader.api.system.SystemProvider;
import dev.plexusmodloader.api.system.brand.GameBrandSystem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SystemRegistryTest {

    private static final ClassLoader LOADER = SystemRegistryTest.class.getClassLoader();

    @Test
    void defaultProviderIsActiveWithoutReplacements() {
        SystemRegistry registry =
                SystemRegistry.create(BuiltInSystems.definitions(), noReplacements(), GameSide.CLIENT, LOADER);

        assertEquals("plexus", registry.get(GameBrandSystem.class).brand(GameSide.CLIENT));
        SystemRegistry.ActiveSystem active = registry.activeSystems().getFirst();
        assertTrue(active.isDefault());
        assertEquals(PolyGameBrandProvider.class.getName(), active.providerClass());
    }

    @Test
    void replacementChangesTheActiveImplementation() {
        SystemRegistry registry = SystemRegistry.create(
                BuiltInSystems.definitions(), replacedBy(TestBrandProvider.class), GameSide.CLIENT, LOADER);

        assertEquals("test/brand", registry.get(GameBrandSystem.class).brand(GameSide.DEDICATED_SERVER));
        assertEquals("branding", registry.activeSystems().getFirst().providerModId());
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                NullProvider.class,
                InvalidBrandProvider.class,
                ThrowingProvider.class,
                NotAProvider.class,
                NoDefaultConstructorProvider.class
            })
    void brokenProvidersStopTheLaunchWithTheModAndProviderNamed(Class<?> provider) {
        SystemRegistry.SystemSetupException failure = assertThrows(
                SystemRegistry.SystemSetupException.class,
                () -> SystemRegistry.create(
                        BuiltInSystems.definitions(), replacedBy(provider), GameSide.CLIENT, LOADER));

        String problem = failure.problems().getFirst();
        assertTrue(
                problem.startsWith("PLEXUS-SYSTEM-003 system=" + GameBrandSystem.ID + " provider=" + provider.getName()
                        + " mod=branding: "),
                problem);
    }

    private static SystemReplacementResolver.Result noReplacements() {
        return new SystemReplacementResolver.Result(Map.of(), List.of());
    }

    private static SystemReplacementResolver.Result replacedBy(Class<?> provider) {
        return new SystemReplacementResolver.Result(
                Map.of(
                        GameBrandSystem.ID,
                        new SystemReplacementResolver.SelectedReplacement(
                                GameBrandSystem.ID, "branding", provider.getName(), List.of())),
                List.of());
    }

    public static final class TestBrandProvider implements SystemProvider<GameBrandSystem> {
        @Override
        public GameBrandSystem create(SystemContext context) {
            return side -> "test/brand";
        }
    }

    public static final class NullProvider implements SystemProvider<GameBrandSystem> {
        @Override
        public GameBrandSystem create(SystemContext context) {
            return null;
        }
    }

    public static final class InvalidBrandProvider implements SystemProvider<GameBrandSystem> {
        @Override
        public GameBrandSystem create(SystemContext context) {
            return side -> " spaces are not allowed around a brand ";
        }
    }

    public static final class ThrowingProvider implements SystemProvider<GameBrandSystem> {
        @Override
        public GameBrandSystem create(SystemContext context) {
            throw new IllegalStateException("boom");
        }
    }

    public static final class NotAProvider {}

    public static final class NoDefaultConstructorProvider implements SystemProvider<GameBrandSystem> {
        public NoDefaultConstructorProvider(String unused) {}

        @Override
        public GameBrandSystem create(SystemContext context) {
            return side -> "unused";
        }
    }
}
