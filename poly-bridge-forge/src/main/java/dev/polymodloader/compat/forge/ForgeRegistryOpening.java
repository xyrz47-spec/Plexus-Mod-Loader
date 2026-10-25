package dev.polymodloader.compat.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Opens Forge's registries for other loaders' mods while their initialization runs (ADR-0021 G8). Fabric mods register
 * blocks, items and the like straight into the game's registries from {@code onInitialize}, as the vanilla game allows
 * then; Forge freezes its registries after the vanilla content ({@code GameData.vanillaSnapshot}) and lets mods add to
 * them only during its {@code RegisterEvent}, for which it unfreezes each registry ({@code
 * GameData.postRegisterEvents}: {@code unfreeze}, the event, {@code freeze}). Here the same happens around the other
 * loaders' initialization: every frozen Forge registry is unfrozen, the game registry that wraps it ({@code
 * NamespacedWrapper}) is unlocked, and both go back to how they were afterwards.
 *
 * <p>Forge's classes are reached through reflection, from the game class loader (checked with {@code javap} on Forge
 * 26.2-65.1.5: {@code RegistryManager.ACTIVE.getRegistries()}, {@code ForgeRegistry.isLocked}/{@code unfreeze}/{@code
 * freeze}/{@code getWrapper}, {@code NamespacedWrapper.locked}/{@code lock}). Thread: the game's main thread, at Minecraft's
 * mod initialization point.
 */
final class ForgeRegistryOpening {

    private ForgeRegistryOpening() {}

    /** Runs {@code initialization} with Forge's registries open for registration. */
    static void run(ClassLoader game, Runnable initialization) throws ReflectiveOperationException {
        Class<?> manager = Class.forName("net.minecraftforge.registries.RegistryManager", true, game);
        Class<?> registryType = Class.forName("net.minecraftforge.registries.ForgeRegistry", true, game);
        Class<?> wrapperType = Class.forName("net.minecraftforge.registries.NamespacedWrapper", true, game);
        Method isLocked = registryType.getMethod("isLocked");
        Method unfreeze = registryType.getMethod("unfreeze");
        Method freeze = accessible(registryType.getDeclaredMethod("freeze"));
        Method wrapper = accessible(registryType.getDeclaredMethod("getWrapper"));
        Field locked = wrapperType.getDeclaredField("locked");
        locked.setAccessible(true);
        Method lock = accessible(wrapperType.getMethod("lock"));

        Object active = manager.getField("ACTIVE").get(null);
        Map<?, ?> registries = (Map<?, ?>) manager.getMethod("getRegistries").invoke(active);
        List<Object> unfrozen = new ArrayList<>();
        List<Object> unlocked = new ArrayList<>();
        try {
            for (Object registry : registries.values()) {
                if (!registryType.isInstance(registry)) {
                    continue;
                }
                if ((boolean) isLocked.invoke(registry)) {
                    unfreeze.invoke(registry);
                    unfrozen.add(registry);
                }
                Object gameRegistry = wrapper.invoke(registry);
                if (gameRegistry != null && locked.getBoolean(gameRegistry)) {
                    locked.setBoolean(gameRegistry, false);
                    unlocked.add(gameRegistry);
                }
            }
            initialization.run();
        } finally {
            for (Object registry : unfrozen) {
                freeze.invoke(registry);
            }
            for (Object gameRegistry : unlocked) {
                lock.invoke(gameRegistry);
            }
        }
    }

    private static Method accessible(Method method) {
        method.setAccessible(true);
        return method;
    }
}
