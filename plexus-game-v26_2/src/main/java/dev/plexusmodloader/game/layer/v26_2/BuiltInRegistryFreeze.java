package dev.plexusmodloader.game.layer.v26_2;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The tag binding in {@code BuiltInRegistries.freeze()} when that freeze runs a second time (ADR-0026 38, rule {@code
 * plexus:builtin_registries_refreeze}). Fabric API's registry sync keeps the built-in registries open past {@code
 * Bootstrap.bootStrap} and freezes them after mod initialization with {@code BuiltInRegistries.bootStrap()}: on the
 * client from {@code Minecraft.<init>} ({@code MinecraftMixin.afterModInit}), on a dedicated server from {@code
 * Main.main} ({@code MainMixin.afterModInit}). Under NeoForge, NeoForge has frozen them by then with {@code
 * GameData.freezeData()} (after its own registration, and again after the other loaders' initialization, {@code
 * NeoForgeRegistryOpening}). For every {@code MappedRegistry} in {@code BuiltInRegistries.REGISTRY}, that does what the
 * second freeze does per registry: {@code bindAllTagsToEmpty()}, then {@code freeze()}. {@code freeze()} on a frozen
 * registry returns at once, but {@code bindAllTagsToEmpty()} fails ({@code Registry is already frozen}), which stopped
 * the client.
 *
 * <p>So for a registry that is already frozen, the binding is left out: {@code MappedRegistry.freeze()} fails on a
 * registry with an unbound tag, so a frozen registry has its tags bound, and binding them again is not possible. For a
 * registry that is not frozen (every registry in vanilla, Fabric and NeoForge alone, which freeze once), the binding runs
 * as the vanilla method does: {@code ((MappedRegistry) registry).bindAllTagsToEmpty()}. Checked with {@code javap} on
 * the 26.2 client and server jars and NeoForge 26.2.0.93's patched client jar: {@code bindBootstrappedTagsToEmpty} is that
 * one cast and call, {@code GameData.freezeData} binds and freezes each {@code MappedRegistry} of the root registry, and
 * {@code MappedRegistry.frozen} is the field {@code validateWrite()} checks.
 *
 * <p>Thread: whichever thread freezes the built-in registries (the game's main thread).
 */
public final class BuiltInRegistryFreeze {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final Field FROZEN = frozenField();

    private BuiltInRegistryFreeze() {}

    /**
     * What {@code BuiltInRegistries.freeze()} calls instead of {@code bindBootstrappedTagsToEmpty(registry)}: that
     * method's binding, left out for a registry that is already frozen.
     *
     * @throws ClassCastException when {@code registry} is not a {@code MappedRegistry}, as the vanilla method throws
     */
    public static void bindBootstrappedTagsToEmpty(Registry<?> registry) {
        MappedRegistry<?> mapped = (MappedRegistry<?>) registry;
        if (frozen(mapped)) {
            if (REPORTED.compareAndSet(false, true)) {
                LOGGER.info(
                        "[Plexus Mod Loader] The built-in registries are frozen a second time ({} first): registries"
                                + " another loader froze keep their tags (ADR-0026 38)",
                        mapped.key());
            }
            return;
        }
        mapped.bindAllTagsToEmpty();
    }

    /** Whether {@code registry} is frozen; {@code false} when the field cannot be read, so the vanilla binding runs. */
    static boolean frozen(MappedRegistry<?> registry) {
        if (FROZEN == null) {
            return false;
        }
        try {
            return FROZEN.getBoolean(registry);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("MappedRegistry.frozen became unreadable after it was opened", e);
        }
    }

    private static Field frozenField() {
        try {
            Field field = MappedRegistry.class.getDeclaredField("frozen");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | InaccessibleObjectException | SecurityException e) {
            LOGGER.warn(
                    "[Plexus Mod Loader] MappedRegistry.frozen cannot be read ({}); a second freeze of the built-in"
                            + " registries binds their tags as vanilla does",
                    e.toString());
            return null;
        }
    }
}
