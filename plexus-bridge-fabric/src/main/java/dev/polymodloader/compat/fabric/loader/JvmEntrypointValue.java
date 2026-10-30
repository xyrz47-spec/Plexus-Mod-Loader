package dev.polymodloader.compat.fabric.loader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Creates an entrypoint from its value in the JVM notation that Fabric Loader's and Quilt Loader's default language
 * adapters share: a class name ({@code a.Main}, created with its no-argument constructor), a static field ({@code
 * a.Main::INSTANCE}), or a method or constructor ({@code a.Main::<init>}) turned into the requested functional interface
 * ({@code a.Main::init}); an instance method is bound to a new instance made with the class's no-argument constructor,
 * as Fabric Loader 0.19.5 and Quilt Loader 0.31.0-beta.4 do. Both default language adapter stand-ins ({@code
 * net.fabricmc.loader.impl.util.DefaultLanguageAdapter}, {@code org.quiltmc.loader.impl.util.DefaultLanguageAdapter})
 * call this, each with its own loader's exception (ADR-0016, ADR-0027).
 *
 * <p>Thread: stateless; safe from any thread.
 */
public final class JvmEntrypointValue {

    private JvmEntrypointValue() {}

    /**
     * Creates the entrypoint {@code value} as {@code type}.
     *
     * @param classLoader the class loader the value's class is loaded with (the game class loader)
     * @param failure makes the adapter's exception from a message and a cause; either may be {@code null}, not both
     * @throws E when the value is malformed, its class or member cannot be found, or the result is not a {@code type}
     */
    public static <T, E extends Exception> T create(
            String value, Class<T> type, ClassLoader classLoader, BiFunction<String, Throwable, E> failure) throws E {
        String[] parts = value.split("::");
        if (parts.length >= 3) {
            throw failure.apply("Invalid handle format: " + value, null);
        }
        Class<?> owner;
        try {
            owner = Class.forName(parts[0], true, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw failure.apply(null, e);
        }
        if (parts.length == 1) {
            if (!type.isAssignableFrom(owner)) {
                throw failure.apply("Class " + owner.getName() + " cannot be cast to " + type.getName() + "!", null);
            }
            try {
                return type.cast(owner.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException e) {
                throw failure.apply(null, e);
            }
        }
        String member = parts[1];
        List<Executable> methods = new ArrayList<>();
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(member)) {
                methods.add(method);
            }
        }
        if (member.equals("<init>")) {
            methods.addAll(Arrays.asList(owner.getDeclaredConstructors()));
        }
        try {
            Field field = owner.getDeclaredField(member);
            if (!methods.isEmpty()) {
                throw failure.apply("Ambiguous " + value + " - refers to both field and method!", null);
            }
            if (!Modifier.isStatic(field.getModifiers())) {
                throw failure.apply("Field " + value + " must be static!", null);
            }
            field.setAccessible(true);
            Object instance = field.get(null);
            if (!type.isInstance(instance)) {
                throw failure.apply("Field " + value + " cannot be cast to " + type.getName() + "!", null);
            }
            return type.cast(instance);
        } catch (NoSuchFieldException e) {
            // Not a field: a method.
        } catch (IllegalAccessException e) {
            throw failure.apply("Field " + value + " cannot be accessed!", e);
        }
        if (!type.isInterface()) {
            throw failure.apply(
                    "Cannot proxy method " + value + " to non-interface type " + type.getName() + "!", null);
        }
        if (methods.size() != 1) {
            throw failure.apply(
                    methods.isEmpty()
                            ? "Could not find " + value + "!"
                            : "Found multiple method entries of name " + value + "!",
                    null);
        }
        Executable executable = methods.getFirst();
        Object receiver = null;
        if (executable instanceof Method method && !Modifier.isStatic(method.getModifiers())) {
            try {
                receiver = owner.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                throw failure.apply(null, e);
            }
        }
        try {
            executable.setAccessible(true);
            MethodHandle handle = executable instanceof Method method
                    ? MethodHandles.lookup().unreflect(method)
                    : MethodHandles.lookup().unreflectConstructor((Constructor<?>) executable);
            if (receiver != null) {
                handle = handle.bindTo(receiver);
            }
            return MethodHandleProxies.asInterfaceInstance(type, handle);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw failure.apply(null, e);
        }
    }
}
