package dev.polymodloader.compat.forge;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Opens Forge's registries for other loaders' mods while their initialization runs (ADR-0021 G8). Fabric mods register
 * blocks, items and the like straight into the game's registries from {@code onInitialize}, as the vanilla game allows
 * then (with Fabric API, whose registry sync holds vanilla's freeze of the built-in registries until after mod
 * initialization). Forge freezes its registries after the vanilla content ({@code GameData.vanillaSnapshot}) and opens
 * them only for its own registration: its loading states {@code UNFREEZE_DATA} ({@code GameData.unfreezeData}: {@code
 * unfreeze()} on every {@code MappedRegistry} in {@code BuiltInRegistries.REGISTRY}), {@code LOAD_REGISTRIES} (each
 * Forge registry unfrozen around its {@code RegisterEvent}) and {@code FREEZE_DATA} ({@code GameData.freezeData}:
 * {@code unfreeze()} then {@code freeze()} on each wrapper, {@code freeze()} on the other registries). The same happens
 * here around the other loaders' initialization, and everything goes back to how it was afterwards, also when it throws:
 *
 * <ul>
 *   <li>every locked Forge registry is unfrozen ({@code ForgeRegistry.unfreeze}, which also clears the {@code locked}
 *       flag of the game registry that wraps it, {@code NamespacedWrapper}) and frozen again;
 *   <li>a wrapper still {@code locked} on its own is unlocked and locked again ({@code lock()});
 *   <li>a wrapper that is frozen ({@code isFrozen()}) is unfrozen and frozen again with its own {@code unfreeze()} and
 *       {@code freeze()}, which also binds the new entries' tags;
 *   <li>every other frozen {@code MappedRegistry} of the root registry (the {@code frozen} field) is unfrozen and frozen
 *       again with {@code unfreeze()} (Forge's patch adds it) and {@code freeze()}.
 * </ul>
 *
 * <p>Vanilla's {@code BuiltInRegistries.freeze()} freezes the wrappers and the other registries in {@code
 * Bootstrap.bootStrap}, before the mod initialization point, unless Fabric API's registry sync holds that freeze. With
 * registry sync left out (under Forge 26.3-66.1.0, {@code POLY-COMPAT-224}), {@code Block.<init>} failed in {@code
 * createIntrusiveHolder} ("Registry minecraft:block is already frozen") and a creative tab in {@code
 * MappedRegistry.register}, because only the {@code locked} flags were opened here.
 *
 * <p>The write checks are read from the class files before anything changes: the boolean fields of {@code
 * NamespacedWrapper} that its {@code register} and {@code validateWrite} methods read must be the ones opened here
 * ({@code locked}, {@code frozen}), those of {@code MappedRegistry} only {@code frozen}; {@code unfreeze()} must write
 * {@code frozen} and the wrapper's {@code isFrozen()} read it. A Forge release that checks something else, or lacks a
 * member used here, stops the launch with {@code POLY-COMPAT-227} before any of those mods runs, instead of failing at
 * their first registration.
 *
 * <p>Forge's and Minecraft's classes are reached through reflection, from the game class loader (checked with {@code
 * javap -c} on Forge 26.1-62.0.9, 26.1.2-64.1.5, 26.2-65.1.5 and 26.3-66.1.0, whose {@code NamespacedWrapper} and
 * patched {@code MappedRegistry} are the same in all four: {@code RegistryManager.ACTIVE.getRegistries()}, {@code
 * ForgeRegistry.isLocked}/{@code unfreeze}/{@code freeze}/{@code getWrapper}; {@code NamespacedWrapper.locked}/{@code
 * lock}, {@code frozen}/{@code isFrozen}/{@code unfreeze}/{@code freeze}, {@code register} reads {@code locked} and
 * {@code validateWrite} {@code frozen}; {@code BuiltInRegistries.REGISTRY}, {@code MappedRegistry.frozen}/{@code
 * unfreeze}/{@code freeze}, {@code validateWrite} reads {@code frozen}). Thread: the game's main thread, at Minecraft's
 * mod initialization point.
 */
final class ForgeRegistryOpening {

    static final String REGISTRY_MANAGER = "net.minecraftforge.registries.RegistryManager";
    static final String FORGE_REGISTRY = "net.minecraftforge.registries.ForgeRegistry";
    static final String NAMESPACED_WRAPPER = "net.minecraftforge.registries.NamespacedWrapper";
    static final String BUILT_IN_REGISTRIES = "net.minecraft.core.registries.BuiltInRegistries";
    static final String MAPPED_REGISTRY = "net.minecraft.core.MappedRegistry";

    /** The registries' flags opened here. */
    static final String LOCKED = "locked";

    static final String FROZEN = "frozen";

    /** The methods that refuse a write, by the flags they read. */
    private static final Set<String> WRITE_CHECKS = Set.of("register", "validateWrite");

    private ForgeRegistryOpening() {}

    /**
     * Runs {@code initialization} with Forge's registries open for registration, and closes them again afterwards,
     * also when it throws.
     *
     * @throws IllegalStateException {@code POLY-COMPAT-227} when Forge's registry classes are not the shape checked
     *     here; {@code initialization} does not run then and nothing is changed
     */
    static void run(ClassLoader game, Runnable initialization) throws ReflectiveOperationException {
        Members members = Members.of(game);
        Map<?, ?> registries = (Map<?, ?>) members.getRegistries.invoke(members.active.get(null));
        Iterable<?> root = (Iterable<?>) members.root.get(null);
        Opened opened = new Opened();
        Throwable failure = null;
        try {
            for (Object registry : registries.values()) {
                if (!members.registryType.isInstance(registry)) {
                    continue;
                }
                if ((boolean) invoke(members.isLocked, registry)) {
                    invoke(members.unfreeze, registry);
                    opened.forgeRegistries.add(registry);
                }
                Object wrapper = invoke(members.getWrapper, registry);
                if (wrapper == null) {
                    continue;
                }
                if (members.locked.getBoolean(wrapper)) {
                    members.locked.setBoolean(wrapper, false);
                    opened.lockedWrappers.add(wrapper);
                }
                if ((boolean) invoke(members.isFrozen, wrapper)) {
                    invoke(members.unfreezeWrapper, wrapper);
                    opened.frozenWrappers.add(wrapper);
                }
            }
            for (Object registry : root) {
                if (members.mappedType.isInstance(registry)
                        && !members.wrapperType.isInstance(registry)
                        && members.mappedFrozen.getBoolean(registry)) {
                    invoke(members.unfreezeMapped, registry);
                    opened.frozenRegistries.add(registry);
                }
            }
            initialization.run();
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            close(members, opened, failure);
        }
    }

    /** What {@link #run} opened, to close again. */
    private static final class Opened {
        final List<Object> forgeRegistries = new ArrayList<>();
        final List<Object> lockedWrappers = new ArrayList<>();
        final List<Object> frozenWrappers = new ArrayList<>();
        final List<Object> frozenRegistries = new ArrayList<>();
    }

    /**
     * Freezes and locks again what {@link #run} opened, in the order of Forge's {@code freezeData} (the game registries,
     * then the Forge registries), all of it even when a step fails. A failure here is thrown, unless {@code
     * initialization} failed first, whose exception then carries it as suppressed.
     */
    private static void close(Members members, Opened opened, Throwable failure) throws ReflectiveOperationException {
        Throwable first = null;
        for (Object wrapper : opened.frozenWrappers) {
            first = attempt(() -> invoke(members.freezeWrapper, wrapper), first);
        }
        for (Object registry : opened.frozenRegistries) {
            first = attempt(() -> invoke(members.freezeMapped, registry), first);
        }
        for (Object registry : opened.forgeRegistries) {
            first = attempt(() -> invoke(members.freeze, registry), first);
        }
        for (Object wrapper : opened.lockedWrappers) {
            first = attempt(() -> invoke(members.lock, wrapper), first);
        }
        if (first == null) {
            return;
        }
        if (failure != null) {
            failure.addSuppressed(first);
            return;
        }
        if (first instanceof ReflectiveOperationException e) {
            throw e;
        }
        if (first instanceof RuntimeException e) {
            throw e;
        }
        throw (Error) first;
    }

    private static Throwable attempt(Step step, Throwable first) {
        try {
            step.run();
            return first;
        } catch (ReflectiveOperationException | RuntimeException | Error e) {
            if (first == null) {
                return e;
            }
            first.addSuppressed(e);
            return first;
        }
    }

    @FunctionalInterface
    private interface Step {
        void run() throws ReflectiveOperationException;
    }

    /** Calls {@code method}, throwing what Forge's code threw as itself rather than wrapped. */
    private static Object invoke(Method method, Object target) throws ReflectiveOperationException {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw e;
        }
    }

    /** Forge's and the game's registry members used here, after the shape check. */
    private record Members(
            Field active,
            Method getRegistries,
            Class<?> registryType,
            Method isLocked,
            Method unfreeze,
            Method freeze,
            Method getWrapper,
            Class<?> wrapperType,
            Field locked,
            Method lock,
            Method isFrozen,
            Method unfreezeWrapper,
            Method freezeWrapper,
            Field root,
            Class<?> mappedType,
            Field mappedFrozen,
            Method unfreezeMapped,
            Method freezeMapped) {

        static Members of(ClassLoader game) {
            Class<?> manager = gameClass(game, REGISTRY_MANAGER);
            Class<?> registryType = gameClass(game, FORGE_REGISTRY);
            Class<?> wrapperType = gameClass(game, NAMESPACED_WRAPPER);
            Class<?> builtIn = gameClass(game, BUILT_IN_REGISTRIES);
            Class<?> mappedType = gameClass(game, MAPPED_REGISTRY);
            List<String> missing = new ArrayList<>();
            Field active = field(manager, "ACTIVE", missing);
            Method getRegistries = method(manager, "getRegistries", missing);
            Method isLocked = method(registryType, "isLocked", missing);
            Method unfreeze = method(registryType, "unfreeze", missing);
            Method freeze = method(registryType, "freeze", missing);
            Method getWrapper = method(registryType, "getWrapper", missing);
            Field locked = booleanField(wrapperType, LOCKED, missing);
            Method lock = method(wrapperType, "lock", missing);
            Method isFrozen = method(wrapperType, "isFrozen", missing);
            Method unfreezeWrapper = method(wrapperType, "unfreeze", missing);
            Method freezeWrapper = method(wrapperType, "freeze", missing);
            Field root = field(builtIn, "REGISTRY", missing);
            Field mappedFrozen = booleanField(mappedType, FROZEN, missing);
            Method unfreezeMapped = method(mappedType, "unfreeze", missing);
            Method freezeMapped = method(mappedType, "freeze", missing);
            if (isFrozen != null && isFrozen.getReturnType() != boolean.class) {
                missing.add(simpleName(wrapperType.getName()) + ".isFrozen() returning a boolean");
            }
            if (!wrapperType.getSuperclass().equals(mappedType)) {
                missing.add(simpleName(wrapperType.getName()) + " as a " + simpleName(MAPPED_REGISTRY));
            }
            if (root != null && !Iterable.class.isAssignableFrom(root.getType())) {
                missing.add(simpleName(BUILT_IN_REGISTRIES) + ".REGISTRY as an Iterable");
            }
            if (!missing.isEmpty()) {
                throw unknownShape("it has no " + String.join(", ", missing));
            }
            checkWriteChecks(classModel(wrapperType), Set.of(LOCKED, FROZEN), true);
            checkWriteChecks(classModel(mappedType), Set.of(FROZEN), false);
            return new Members(
                    active,
                    getRegistries,
                    registryType,
                    isLocked,
                    unfreeze,
                    freeze,
                    getWrapper,
                    wrapperType,
                    locked,
                    lock,
                    isFrozen,
                    unfreezeWrapper,
                    freezeWrapper,
                    root,
                    mappedType,
                    mappedFrozen,
                    unfreezeMapped,
                    freezeMapped);
        }

        private static ClassModel classModel(Class<?> type) {
            String resource = simpleName(type.getName()) + ".class";
            try (InputStream in = type.getResourceAsStream(resource)) {
                if (in == null) {
                    throw unknownShape("the class file of " + type.getName() + " cannot be read");
                }
                return ClassFile.of().parse(in.readAllBytes());
            } catch (IOException | IllegalArgumentException e) {
                throw unknownShape("the class file of " + type.getName() + " cannot be read (" + e + ")");
            }
        }

        private static Class<?> gameClass(ClassLoader game, String name) {
            try {
                return Class.forName(name, true, game);
            } catch (ClassNotFoundException e) {
                throw unknownShape("it has no class " + name);
            }
        }

        private static Field field(Class<?> owner, String name, List<String> missing) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                missing.add(simpleName(owner.getName()) + "." + name);
                return null;
            }
        }

        private static Field booleanField(Class<?> owner, String name, List<String> missing) {
            Field field = field(owner, name, missing);
            if (field != null && field.getType() != boolean.class) {
                missing.add(simpleName(owner.getName()) + "." + name + " as a boolean");
                return null;
            }
            return field;
        }

        private static Method method(Class<?> owner, String name, List<String> missing) {
            try {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                missing.add(simpleName(owner.getName()) + "." + name + "()");
                return null;
            }
        }
    }

    /**
     * Checks a registry class's write checks, from its class file: the boolean fields of the class that its {@code
     * register} and {@code validateWrite} methods read must be among {@code opened}, and {@code unfreeze()} must write
     * {@code frozen}; with {@code isFrozen}, {@code isFrozen()} must read {@code frozen}.
     *
     * @throws IllegalStateException {@code POLY-COMPAT-227} when they are not
     */
    static void checkWriteChecks(ClassModel model, Set<String> opened, boolean isFrozen) {
        Set<String> checked = new TreeSet<>();
        boolean writeChecks = false;
        Set<String> isFrozenReads = new TreeSet<>();
        Set<String> unfreezeWrites = new TreeSet<>();
        for (MethodModel method : model.methods()) {
            String name = method.methodName().stringValue();
            String descriptor = method.methodType().stringValue();
            if (WRITE_CHECKS.contains(name)) {
                writeChecks = true;
                checked.addAll(booleanFields(model, method, Opcode.GETFIELD));
            } else if (name.equals("isFrozen") && descriptor.equals("()Z")) {
                isFrozenReads.addAll(booleanFields(model, method, Opcode.GETFIELD));
            } else if (name.equals("unfreeze") && descriptor.equals("()V")) {
                unfreezeWrites.addAll(booleanFields(model, method, Opcode.PUTFIELD));
            }
        }
        String type = simpleName(model.thisClass().asInternalName().replace('/', '.'));
        if (!writeChecks) {
            throw unknownShape(type + " has no register or validateWrite method to check");
        }
        Set<String> unknown = new TreeSet<>(checked);
        unknown.removeAll(opened);
        if (!unknown.isEmpty()) {
            throw unknownShape(type + "'s register/validateWrite check " + String.join(", ", unknown)
                    + ", which Poly Mod Loader does not open (it opens " + String.join(", ", new TreeSet<>(opened))
                    + ")");
        }
        if (!unfreezeWrites.equals(Set.of(FROZEN))) {
            throw unknownShape(
                    type + ".unfreeze() writes " + unfreezeWrites + ", where Poly Mod Loader expects " + FROZEN);
        }
        if (isFrozen && !isFrozenReads.equals(Set.of(FROZEN))) {
            throw unknownShape(
                    type + ".isFrozen() reads " + isFrozenReads + ", where Poly Mod Loader expects " + FROZEN);
        }
    }

    /** The boolean fields of the class itself that {@code method} reads or writes with {@code opcode}. */
    private static Set<String> booleanFields(ClassModel model, MethodModel method, Opcode opcode) {
        String owner = model.thisClass().asInternalName();
        Set<String> fields = new TreeSet<>();
        method.code()
                .ifPresent(code -> code.forEach(element -> {
                    if (element instanceof FieldInstruction instruction
                            && instruction.opcode() == opcode
                            && instruction.owner().asInternalName().equals(owner)
                            && instruction.typeSymbol().equals(ConstantDescs.CD_boolean)) {
                        fields.add(instruction.name().stringValue());
                    }
                }));
        return fields;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static IllegalStateException unknownShape(String detail) {
        return new IllegalStateException("POLY-COMPAT-227 MinecraftForge's registries are not the code Poly Mod Loader"
                + " checked (" + detail + "), so Poly Mod Loader cannot open them for Fabric and Quilt mods to register"
                + " into, and stops before those mods run. This is a limitation of Poly Mod Loader with this Forge"
                + " release: report it to Poly Mod Loader, or play without the Fabric and Quilt mods (Forge mods run"
                + " alone).");
    }
}
