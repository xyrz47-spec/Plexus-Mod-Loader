package com.example.compat.hostpatch;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exercises Fabric API features whose mixins meet members MinecraftForge's patch added or changed, the way Fabric API
 * itself uses them, and prints what happens (checked by {@code corpusFabricForge}). The game and Fabric API are
 * reached by reflection, so the probe needs neither to compile. Each check that cannot run prints why instead.
 *
 * <ul>
 *   <li>{@code fabric-networking-api-v1}: casts a server packet listener to {@code ServerCommonPacketListenerImplAccessor}
 *       and calls {@code getConnection()} (Forge added that method) and {@code getServer()} (Mixin generates it).
 *   <li>{@code fabric-tag-api-v1}: the item tag {@code poly_host_patch_probe:removals} lists stone, dirt and sand,
 *       removes sand with Forge's {@code "remove"} and dirt with Fabric's {@code "fabric:remove"}; only stone stays when
 *       both work.
 *   <li>{@code fabric-object-builder-api-v1}: casts {@code AttributeSupplier.builder()} to {@code
 *       AttributeSupplierBuilderAccessor} and calls {@code getBuilder()}, whose field Forge changed to another type.
 *   <li>{@code fabric-data-generation-api-v1}: whether {@code Advancement$Builder} implements {@code
 *       FabricAdvancementBuilder}.
 * </ul>
 */
public final class HostPatchProbe {

    private static final String PREFIX = "[poly_host_patch_probe] ";

    private HostPatchProbe() {}

    /** Runs every check; {@code server} is the running {@code MinecraftServer}. */
    public static void run(Object server) {
        ClassLoader loader = server.getClass().getClassLoader();
        check("fabric-networking-api-v1", () -> networking(server, loader));
        check("fabric-tag-api-v1", () -> tags(loader));
        check("fabric-object-builder-api-v1", () -> attributes(loader));
        check("fabric-data-generation-api-v1", () -> advancements(loader));
    }

    private interface Check {
        String run() throws ReflectiveOperationException;
    }

    private static void check(String module, Check check) {
        String result;
        try {
            result = check.run();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            result = "failed: " + e;
        }
        System.out.println(PREFIX + module + ": " + result);
    }

    private static String networking(Object server, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> common = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl", false, loader);
        Class<?> accessor = Class.forName(
                "net.fabricmc.fabric.mixin.networking.accessor.ServerCommonPacketListenerImplAccessor", false, loader);
        boolean implemented = accessor.isAssignableFrom(common);
        // A game packet listener with a connection and the server, without a client: allocated, fields set.
        Object listener =
                allocate(Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl", false, loader));
        Object connection = allocate(Class.forName("net.minecraft.network.Connection", false, loader));
        set(common, listener, "connection", connection);
        set(common, listener, "server", server);
        Object viaAccessor = accessor.cast(listener);
        Object gotConnection = accessor.getMethod("getConnection").invoke(viaAccessor);
        Object gotServer = accessor.getMethod("getServer").invoke(viaAccessor);
        return "ServerCommonPacketListenerImpl implements ServerCommonPacketListenerImplAccessor: " + implemented
                + "; getConnection() is the listener's connection: " + (gotConnection == connection)
                + "; getServer() is the listener's server: " + (gotServer == server);
    }

    private static String tags(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> identifier = Class.forName("net.minecraft.resources.Identifier", false, loader);
        Class<?> resourceKey = Class.forName("net.minecraft.resources.ResourceKey", false, loader);
        Class<?> tagKey = Class.forName("net.minecraft.tags.TagKey", false, loader);
        Class<?> registry = Class.forName("net.minecraft.core.Registry", false, loader);
        Class<?> holder = Class.forName("net.minecraft.core.Holder", false, loader);
        Object items = Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, loader)
                .getField("ITEM")
                .get(null);
        Object itemsKey = Class.forName("net.minecraft.core.registries.Registries", true, loader)
                .getField("ITEM")
                .get(null);
        Object id = identifier.getMethod("parse", String.class).invoke(null, "poly_host_patch_probe:removals");
        Object key = tagKey.getMethod("create", resourceKey, identifier).invoke(null, itemsKey, id);
        List<String> entries = new ArrayList<>();
        for (Object entry :
                (Iterable<?>) registry.getMethod("getTagOrEmpty", tagKey).invoke(items, key)) {
            Object item = holder.getMethod("value").invoke(entry);
            entries.add(
                    String.valueOf(registry.getMethod("getKey", Object.class).invoke(items, item)));
        }
        entries.sort(null);
        return "tag poly_host_patch_probe:removals = " + entries;
    }

    private static String attributes(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> builder =
                Class.forName("net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder", false, loader);
        Class<?> accessor = Class.forName(
                "net.fabricmc.fabric.mixin.object.builder.AttributeSupplierBuilderAccessor", false, loader);
        Object created = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeSupplier", true, loader)
                .getMethod("builder")
                .invoke(null);
        String prefix = "AttributeSupplier$Builder implements AttributeSupplierBuilderAccessor: "
                + accessor.isAssignableFrom(builder) + "; getBuilder() ";
        try {
            Object result = accessor.getMethod("getBuilder").invoke(accessor.cast(created));
            return prefix + "returns "
                    + (result == null ? "null" : result.getClass().getName());
        } catch (InvocationTargetException e) {
            return prefix + "throws " + e.getCause().getClass().getSimpleName() + ": "
                    + e.getCause().getMessage();
        }
    }

    private static String advancements(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> builder = Class.forName("net.minecraft.advancements.Advancement$Builder", false, loader);
        Class<?> fabric =
                Class.forName("net.fabricmc.fabric.api.datagen.v1.advancement.FabricAdvancementBuilder", false, loader);
        Method save = fabric.getMethod(
                "save", Consumer.class, Class.forName("net.minecraft.resources.Identifier", false, loader));
        Method resolved = builder.getMethod(save.getName(), save.getParameterTypes());
        return "Advancement$Builder implements FabricAdvancementBuilder: " + fabric.isAssignableFrom(builder)
                + "; save(Consumer, Identifier) is declared by "
                + resolved.getDeclaringClass().getSimpleName();
    }

    private static Object allocate(Class<?> type) throws ReflectiveOperationException {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }

    private static void set(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
