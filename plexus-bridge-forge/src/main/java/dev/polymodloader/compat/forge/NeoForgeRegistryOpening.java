package dev.polymodloader.compat.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Opens NeoForge's registries for other loaders' mods while their initialization runs (ADR-0026 31), as {@link
 * ForgeRegistryOpening} does for Forge. Fabric mods register blocks, items and the like straight into the game's
 * registries from {@code onInitialize}; NeoForge lets mods add to them only during its {@code RegisterEvent}, for which
 * its {@code CommonModLoader} calls {@code GameData.unfreezeData()}, posts the events and calls {@code
 * GameData.freezeData()} (which binds unbound tags to empty, freezes and takes NeoForge's frozen snapshot). The same two
 * calls run here around the other loaders' initialization, and the root registry (where Fabric's registry builder adds
 * new registries) is unfrozen and frozen with them. When the registries are not frozen (NeoForge's own registration is
 * running), nothing changes.
 *
 * <p>NeoForge's and Minecraft's classes are reached through reflection, from the game class loader (checked with {@code
 * javap} on NeoForge 26.2.0.93 and 26.1.0.19-beta: {@code GameData.unfreezeData}/{@code freezeData}, {@code
 * MappedRegistry.frozen}/{@code unfreeze(boolean)}/{@code freeze}, {@code BuiltInRegistries.REGISTRY}/{@code ITEM}).
 * Thread: the game's main thread, at Minecraft's mod initialization point.
 */
final class NeoForgeRegistryOpening {

    private NeoForgeRegistryOpening() {}

    /** Runs {@code initialization} with NeoForge's registries open for registration. */
    static void run(ClassLoader game, Runnable initialization) throws ReflectiveOperationException {
        Class<?> gameData = Class.forName("net.neoforged.neoforge.registries.GameData", true, game);
        Class<?> builtIn = Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, game);
        Class<?> mapped = Class.forName("net.minecraft.core.MappedRegistry", true, game);
        Field frozen = mapped.getDeclaredField("frozen");
        frozen.setAccessible(true);
        Method unfreeze = mapped.getMethod("unfreeze", boolean.class);
        Method freeze = mapped.getMethod("freeze");
        Object root = builtIn.getField("REGISTRY").get(null);
        Object items = builtIn.getField("ITEM").get(null);
        if (!mapped.isInstance(items) || !frozen.getBoolean(items)) {
            initialization.run();
            return;
        }
        boolean rootFrozen = mapped.isInstance(root) && frozen.getBoolean(root);
        if (rootFrozen) {
            // false: the root keeps its tags, which freezeData does not bind again for it.
            unfreeze.invoke(root, false);
        }
        gameData.getMethod("unfreezeData").invoke(null);
        try {
            initialization.run();
        } finally {
            gameData.getMethod("freezeData").invoke(null);
            if (rootFrozen && !frozen.getBoolean(root)) {
                freeze.invoke(root);
            }
        }
    }
}
