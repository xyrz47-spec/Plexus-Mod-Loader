package dev.plexusmodloader.game.layer.v26_1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/**
 * ADR-0026 38: Fabric API freezes the built-in registries after mod initialization ({@code BuiltInRegistries.bootStrap()}
 * from its {@code afterModInit}); under NeoForge they are frozen already, and the vanilla tag binding of that second
 * freeze stopped the client ({@code corpusFabricNeoForgeClient}, {@code Registry is already frozen}).
 */
class BuiltInRegistryFreezeTest {

    private static MappedRegistry<String> registry(String name) {
        ResourceKey<Registry<String>> key = ResourceKey.createRegistryKey(Identifier.withDefaultNamespace(name));
        return new MappedRegistry<>(key, Lifecycle.stable());
    }

    @Test
    void theVanillaBindingFailsOnARegistryAnotherLoaderFroze() {
        MappedRegistry<String> frozen = registry("poly_test_frozen_vanilla");
        frozen.bindAllTagsToEmpty();
        frozen.freeze();

        IllegalStateException failure = assertThrows(IllegalStateException.class, frozen::bindAllTagsToEmpty);
        assertEquals("Registry is already frozen", failure.getMessage());
    }

    @Test
    void aSecondFreezeLeavesTheBindingOutForAFrozenRegistry() {
        MappedRegistry<String> frozen = registry("poly_test_frozen");
        frozen.bindAllTagsToEmpty();
        frozen.freeze();

        BuiltInRegistryFreeze.bindBootstrappedTagsToEmpty(frozen);
        frozen.freeze();

        assertTrue(BuiltInRegistryFreeze.frozen(frozen));
    }

    @Test
    void aFirstFreezeBindsAsVanillaDoes() {
        MappedRegistry<String> open = registry("poly_test_open");

        assertFalse(BuiltInRegistryFreeze.frozen(open));
        BuiltInRegistryFreeze.bindBootstrappedTagsToEmpty(open);
        open.freeze();

        assertTrue(BuiltInRegistryFreeze.frozen(open));
    }
}
