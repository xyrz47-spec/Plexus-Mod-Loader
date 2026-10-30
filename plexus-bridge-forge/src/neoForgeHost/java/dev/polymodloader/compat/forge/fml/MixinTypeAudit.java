package dev.polymodloader.compat.forge.fml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.reflect.AccessFlag;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The part of the Mixin audit ({@code -Dpoly.neoforge.mixinAudit=true}) that finds code that would throw a {@code
 * ClassCastException} once the game runs (ADR-0026 39). A mixin gives its target class its interfaces, and an accessor or
 * invoker mixin makes the target implement the mixin itself. When such a mixin is left out (Poly Mod Loader's {@code
 * 138}) or fails to apply, the target lacks the type, and other mixins that cast the game's object to it fail only when
 * that code runs: Fabric API's {@code GameRendererMixin} casts {@code GuiRenderer} to {@code GuiRendererExtensions}, which
 * {@code GuiRendererMixin} adds and which NeoForge's patch left out, in {@code GameRenderer.<init>}, after the game
 * window opened. Loading the classes, as the audit does, does not run that code.
 *
 * <p>So the audit reads every mixin of every configuration as its jar has it (the configuration resource before Poly
 * Mod Loader's changes, so left-out mixins count), loads each target, and takes each type the target does not have as
 * loaded as missing. Then it looks through the game classes as Mixin and FML's processors transformed them during the
 * audit: a cast, {@code instanceof} or call on a missing type is a failure.
 *
 * <p>Thread: the audit's thread; {@link #record} from class loading threads.
 */
final class MixinTypeAudit {

    private static final Map<String, byte[]> TRANSFORMED = new ConcurrentHashMap<>();
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final Pattern LIST = Pattern.compile("\"(mixins|client|server)\"\\s*:\\s*\\[([^\\]]*)]");
    private static final Pattern PACKAGE = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern STRING = Pattern.compile("\"([^\"]*)\"");

    private MixinTypeAudit() {}

    /** Keeps {@code bytes}, the class {@code className} as the processors left it, for {@link #check}. */
    static void record(String className, byte[] bytes) {
        TRANSFORMED.put(className, bytes);
    }

    /**
     * The failures: code in the transformed game classes that uses a type a mixin should have given its target but the
     * target as loaded does not have. {@code notes} gets the missing types that nothing uses.
     *
     * @param configs Mixin's configurations ({@code MixinConfig}: {@code getName()}, {@code getMixinPackage()})
     * @param client whether the client's mixins ({@code mixins} and {@code client}) or the server's apply
     */
    static List<String> check(List<?> configs, ClassLoader classes, boolean client, List<String> notes)
            throws ReflectiveOperationException {
        Map<String, String> missing = new TreeMap<>();
        for (Object config : configs) {
            // MixinConfig is not public: its public getName() is reached through an opened Method.
            java.lang.reflect.Method getName = config.getClass().getMethod("getName");
            getName.setAccessible(true);
            String name = (String) getName.invoke(config);
            byte[] json = resource(classes, name);
            if (json == null) {
                notes.add("configuration " + name + " not readable as a resource; its mixins' types are not checked");
                continue;
            }
            String text = new String(json, StandardCharsets.UTF_8);
            Matcher packageName = PACKAGE.matcher(text);
            if (!packageName.find()) {
                continue;
            }
            String mixinPackage = packageName.group(1);
            Matcher lists = LIST.matcher(text);
            while (lists.find()) {
                String side = lists.group(1);
                if (side.equals(client ? "server" : "client")) {
                    continue;
                }
                Matcher entries = STRING.matcher(lists.group(2));
                while (entries.find()) {
                    String mixin = mixinPackage + "." + entries.group(1);
                    byte[] bytes = resource(classes, mixin.replace('.', '/') + ".class");
                    if (bytes != null) {
                        missingTypes(ClassFile.of().parse(bytes), name, classes, missing);
                    }
                }
            }
        }
        List<String> failures = new ArrayList<>();
        Map<String, List<String>> users = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> transformed : new TreeMap<>(TRANSFORMED).entrySet()) {
            ClassModel model;
            try {
                model = ClassFile.of().parse(transformed.getValue());
            } catch (IllegalArgumentException e) {
                notes.add(transformed.getKey() + " not readable after transformation: " + e.getMessage());
                continue;
            }
            Class<?> owner = loaded(transformed.getKey(), classes);
            Set<String> called = calledOwnMethods(model);
            for (MethodModel method : model.methods()) {
                // A private method nothing in the class calls never runs: a handler Poly Mod Loader took the
                // injection off (138) is merged as such a method.
                if (method.code().isEmpty()
                        || (method.flags().has(AccessFlag.PRIVATE)
                                && !called.contains(method.methodName().stringValue()
                                        + method.methodType().stringValue()))) {
                    continue;
                }
                Map<String, String> uses = new LinkedHashMap<>();
                for (String type : uncheckedUses(method, missing.keySet())) {
                    uses.put(
                            type,
                            transformed.getKey() + "." + method.methodName().stringValue());
                }
                // A mod class the game's code calls (Lithium's Level handler calls HopperHelper, which casts the
                // level to BlockEntityGetter; ADR-0026 43): its called method's unchecked uses count too.
                reachedUses(method, classes, missing.keySet(), new HashSet<>(), 2)
                        .forEach((type, where) -> uses.putIfAbsent(
                                type,
                                transformed.getKey() + "." + method.methodName().stringValue() + " -> " + where));
                for (Map.Entry<String, String> use0 : uses.entrySet()) {
                    String type = use0.getKey();
                    // A type's own code, or that of a class that has it, uses it on objects that have it.
                    if (type.equals(model.thisClass().asInternalName())
                            || (owner != null && implementsType(owner, type.replace('/', '.')))) {
                        continue;
                    }
                    String use = use0.getValue();
                    List<String> found = users.computeIfAbsent(type, key -> new ArrayList<>());
                    if (!found.contains(use)) {
                        found.add(use);
                    }
                }
            }
        }
        missing.forEach((type, how) -> {
            List<String> found = users.get(type);
            if (found == null) {
                notes.add(type + " is missing (" + how + "); no transformed game class uses it");
            } else {
                failures.add(String.join(", ", found) + " uses " + type + ", but " + how
                        + "; that code throws ClassCastException when it runs");
            }
        });
        return failures;
    }

    /**
     * The failures of another kind (ADR-0026 42): a game class or interface, as loaded during the audit, that inherits
     * two default methods of the same name and descriptor from unrelated interfaces and does not declare it (nor does a
     * superclass). The JVM throws {@code IncompatibleClassChangeError: Conflicting default methods} on each call: NeoForge's
     * {@code BlockStateModelExtension} and Fabric API's {@code FabricBlockStateModel} on {@code BlockStateModel}. {@code
     * notes} gets the classes whose methods could not be read.
     */
    static List<String> conflictingDefaults(ClassLoader classes, List<String> notes) {
        List<String> failures = new ArrayList<>();
        for (String name : new TreeMap<>(TRANSFORMED).keySet()) {
            Class<?> type = loaded(name, classes);
            if (type == null) {
                continue;
            }
            try {
                conflicts(type)
                        .forEach((method, declaring) -> failures.add(type.getName() + " inherits the default "
                                + method + " from " + String.join(" and ", declaring)
                                + " and does not declare it; a call throws IncompatibleClassChangeError"));
            } catch (LinkageError e) {
                notes.add(type.getName() + "'s methods are not readable (" + e + "); its defaults are not checked");
            }
        }
        return failures;
    }

    /** {@code type}'s inherited defaults that more than one maximally specific interface gives, to those interfaces. */
    private static Map<String, List<String>> conflicts(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        if (type.isInterface()) {
            interfaces.add(type);
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                addInterfaces(implemented, interfaces);
            }
        }
        Set<String> classMethods = new HashSet<>();
        if (!type.isInterface()) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (java.lang.reflect.Method method : current.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        classMethods.add(key(method));
                    }
                }
            }
        }
        // Each interface's own instance methods, abstract or default, by name and descriptor.
        Map<String, List<java.lang.reflect.Method>> declarations = new TreeMap<>();
        for (Class<?> face : interfaces) {
            for (java.lang.reflect.Method method : face.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (!java.lang.reflect.Modifier.isStatic(modifiers)
                        && !java.lang.reflect.Modifier.isPrivate(modifiers)
                        && !method.isSynthetic()) {
                    declarations
                            .computeIfAbsent(key(method), key -> new ArrayList<>())
                            .add(method);
                }
            }
        }
        Map<String, List<String>> found = new TreeMap<>();
        declarations.forEach((key, methods) -> {
            if (classMethods.contains(key)) {
                return;
            }
            List<String> defaults = new ArrayList<>();
            for (java.lang.reflect.Method method : methods) {
                // Maximally specific: no other declaration is in a subinterface of this one's interface.
                boolean specific = methods.stream()
                        .noneMatch(other -> other != method
                                && other.getDeclaringClass() != method.getDeclaringClass()
                                && method.getDeclaringClass().isAssignableFrom(other.getDeclaringClass()));
                if (specific && method.isDefault()) {
                    defaults.add(method.getDeclaringClass().getName());
                }
            }
            if (defaults.size() > 1) {
                found.put(key, defaults);
            }
        });
        return found;
    }

    private static void addInterfaces(Class<?> face, Set<Class<?>> into) {
        if (into.add(face)) {
            for (Class<?> parent : face.getInterfaces()) {
                addInterfaces(parent, into);
            }
        }
    }

    private static String key(java.lang.reflect.Method method) {
        return method.getName()
                + java.lang.invoke.MethodType.methodType(method.getReturnType(), method.getParameterTypes())
                        .toMethodDescriptorString();
    }

    /** The class's own methods ({@code name + descriptor}) that its code calls or makes method handles of. */
    private static Set<String> calledOwnMethods(ClassModel model) {
        String self = model.thisClass().asInternalName();
        Set<String> called = new HashSet<>();
        for (MethodModel method : model.methods()) {
            if (method.code().isEmpty()) {
                continue;
            }
            for (CodeElement element : method.code().orElseThrow()) {
                if (element instanceof InvokeInstruction call
                        && call.owner().asInternalName().equals(self)) {
                    called.add(call.name().stringValue() + call.type().stringValue());
                } else if (element instanceof java.lang.classfile.instruction.InvokeDynamicInstruction indy) {
                    for (java.lang.constant.ConstantDesc argument : indy.bootstrapArgs()) {
                        if (argument instanceof java.lang.constant.DirectMethodHandleDesc handle
                                && handle.owner().descriptorString().equals("L" + self + ";")) {
                            called.add(handle.methodName() + handle.lookupDescriptor());
                        }
                    }
                }
            }
        }
        return called;
    }

    /**
     * The types of {@code types} that {@code method} casts to or calls on without an {@code instanceof} check of them in
     * the same method: a checked use does not throw when the object lacks the type.
     */
    private static Set<String> uncheckedUses(MethodModel method, Set<String> types) {
        Set<String> checked = new HashSet<>();
        Set<String> used = new LinkedHashSet<>();
        Set<String> classConstants = new HashSet<>();
        boolean reflectiveCheck = false;
        for (CodeElement element : method.code().orElseThrow()) {
            // T.class.isAssignableFrom(...) or T.class.isInstance(...) checks T, as Lithium's BlockInfoInitializer
            // does.
            if (element instanceof java.lang.classfile.instruction.ConstantInstruction constant
                    && constant.constantValue() instanceof java.lang.constant.ClassDesc type
                    && type.isClassOrInterface()) {
                classConstants.add(type.descriptorString()
                        .substring(1, type.descriptorString().length() - 1));
            } else if (element instanceof InvokeInstruction call
                    && call.owner().asInternalName().equals("java/lang/Class")
                    && (call.name().equalsString("isAssignableFrom")
                            || call.name().equalsString("isInstance"))) {
                reflectiveCheck = true;
            }
            if (element instanceof TypeCheckInstruction check) {
                String type = check.type().asInternalName();
                if (check.opcode() == Opcode.INSTANCEOF) {
                    checked.add(type);
                } else if (types.contains(type)) {
                    used.add(type);
                }
            } else if (element instanceof InvokeInstruction call
                    && types.contains(call.owner().asInternalName())) {
                used.add(call.owner().asInternalName());
            }
        }
        used.removeAll(checked);
        if (reflectiveCheck) {
            used.removeAll(classConstants);
        }
        return used;
    }

    /**
     * The missing types that the methods {@code method} calls in classes outside the transformed game classes (a mod's
     * own classes, read as resources) use unchecked, to where, following such calls {@code depth} levels deep.
     */
    private static Map<String, String> reachedUses(
            MethodModel method, ClassLoader classes, Set<String> types, Set<String> seen, int depth) {
        Map<String, String> found = new LinkedHashMap<>();
        if (depth == 0 || method.code().isEmpty()) {
            return found;
        }
        for (var element : method.code().get()) {
            if (!(element instanceof InvokeInstruction call)) {
                continue;
            }
            String owner = call.owner().asInternalName();
            String key = owner + "." + call.name().stringValue() + call.type().stringValue();
            if (owner.startsWith("java/")
                    || owner.startsWith("net/minecraft/")
                    || TRANSFORMED.containsKey(owner.replace('/', '.'))
                    || !seen.add(key)) {
                continue;
            }
            byte[] bytes = resource(classes, owner + ".class");
            if (bytes == null) {
                continue;
            }
            ClassModel callee;
            try {
                callee = ClassFile.of().parse(bytes);
            } catch (IllegalArgumentException e) {
                continue;
            }
            for (MethodModel candidate : callee.methods()) {
                if (candidate.code().isEmpty()
                        || !candidate.methodName().equals(call.name())
                        || !candidate.methodType().equals(call.type())) {
                    continue;
                }
                String where = owner + "." + call.name().stringValue();
                for (String type : uncheckedUses(candidate, types)) {
                    if (!type.equals(owner)) {
                        found.putIfAbsent(type, where);
                    }
                }
                reachedUses(candidate, classes, types, seen, depth - 1)
                        .forEach((type, deeper) -> found.putIfAbsent(type, where + " -> " + deeper));
            }
        }
        return found;
    }

    /**
     * The types {@code mixin} gives its targets that a loaded target does not have, into {@code missing}: its
     * interfaces, and the mixin itself when it is an interface (accessor or invoker mixin).
     */
    private static void missingTypes(
            ClassModel mixin, String config, ClassLoader classes, Map<String, String> missing) {
        List<String> types = new ArrayList<>();
        for (ClassEntry added : mixin.interfaces()) {
            types.add(added.asInternalName());
        }
        String self = mixin.thisClass().asInternalName();
        if (mixin.flags().has(AccessFlag.INTERFACE)) {
            types.add(self);
        }
        if (types.isEmpty()) {
            return;
        }
        for (String target : targets(mixin)) {
            Class<?> targetClass = loaded(target, classes);
            if (targetClass == null) {
                continue;
            }
            // A mixin onto an interface adds members to it; the interface does not implement the mixin.
            List<String> given = targetClass.isInterface()
                    ? types.stream().filter(type -> !type.equals(self)).toList()
                    : types;
            for (String type : given) {
                if (!implementsType(targetClass, type.replace('/', '.'))) {
                    missing.putIfAbsent(
                            type,
                            "the mixin " + self + " of " + config + " gives it to " + target
                                    + ", which as loaded does not have it (the mixin was left out or did not apply)");
                }
            }
        }
    }

    /**
     * Whether {@code type} has the interface {@code name} through its class hierarchy, by name: a mixin class (an
     * accessor mixin is one) must not be loaded, which Mixin refuses.
     */
    private static boolean implementsType(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (implemented.getName().equals(name) || implementsType(implemented, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Class<?> loaded(String internalName, ClassLoader classes) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, classes);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (ClassNotFoundException | RuntimeException | Error e) {
            // The audit's own loading reports targets that fail to load; one that is absent has nothing to check.
            return null;
        }
    }

    /** The mixin's targets ({@code @Mixin} {@code value} classes and {@code targets} names), as internal names. */
    private static List<String> targets(ClassModel mixin) {
        List<String> targets = new ArrayList<>();
        for (Attribute<?> attribute : mixin.attributes()) {
            List<Annotation> annotations = attribute instanceof RuntimeInvisibleAnnotationsAttribute invisible
                    ? invisible.annotations()
                    : attribute instanceof RuntimeVisibleAnnotationsAttribute visible
                            ? visible.annotations()
                            : List.of();
            for (Annotation annotation : annotations) {
                if (!annotation.className().equalsString(MIXIN)) {
                    continue;
                }
                annotation.elements().forEach(element -> {
                    if (element.value() instanceof AnnotationValue.OfArray array) {
                        for (AnnotationValue value : array.values()) {
                            if (value instanceof AnnotationValue.OfClass type) {
                                String descriptor = type.className().stringValue();
                                targets.add(descriptor.substring(1, descriptor.length() - 1));
                            } else if (value instanceof AnnotationValue.OfString name) {
                                targets.add(name.stringValue().replace('.', '/'));
                            }
                        }
                    }
                });
            }
        }
        return targets;
    }

    private static byte[] resource(ClassLoader classes, String name) {
        try (InputStream in = classes.getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null; // reported by the caller as not readable
        }
    }
}
