package dev.polymodloader.runtime.compat;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgedAccessWidening;
import dev.polymodloader.compat.bridge.BridgedInterfaceInjection;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.ModLoaderBridge;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Checks, before a mod of another loader runs, that the classes, methods and fields its class files
 * reference exist in what this launch provides: the Minecraft jar of this version, the libraries and JDK on
 * Poly Mod Loader's class path, the parts of the foreign loader's runtime that Poly Mod Loader provides, and the other
 * installed mods. A mod built for another Minecraft version, or one that needs Fabric Loader's runtime,
 * would otherwise fail with {@link NoSuchMethodError} or {@link NoClassDefFoundError} in the middle of a
 * game; this turns that into a reason given at startup.
 *
 * <p>Findings:
 *
 * <ul>
 *   <li>{@code POLY-COMPAT-111} (blocking): a class of the loader's runtime that Poly Mod Loader does not provide, or a
 *       method or field missing from a runtime class Poly Mod Loader provides only in part (its stand-ins of Fabric Loader's
 *       internal classes)
 *   <li>{@code POLY-COMPAT-112} (blocking): a Minecraft class, method or field this game version does not have
 *   <li>{@code POLY-COMPAT-113} (not blocking): classes no installed mod has, usually an optional integration
 *       with a mod that is not installed; also client classes referenced on a dedicated server, which Fabric
 *       mods keep in client-only code; and Minecraft members this game lacks when their type is such a class or the
 *       class using them is another loader's code path (it uses NeoForge's or Forge's API, which nothing installed has)
 *   <li>{@code POLY-COMPAT-118} (blocking): class files newer than this Java runtime reads
 *   <li>{@code POLY-COMPAT-119} (blocking): a declared entrypoint class that is not in any installed jar
 *   <li>{@code POLY-COMPAT-128} (blocking): an access widening (Fabric class tweaker line) whose class is on
 *       Poly Mod Loader's class path but not in the game jar; Poly Mod Loader widens only game classes
 *   <li>{@code POLY-COMPAT-137} (not blocking): an access widening or interface injection whose class or exact member
 *       this game version does not have; it is not applied, as Fabric Loader applies such a line to nothing
 *   <li>{@code POLY-COMPAT-113} also covers JDK incubator classes ({@code jdk.incubator.*}), which exist only when
 *       their module is enabled; mods use them behind a check
 *   <li>{@code POLY-COMPAT-130} (not blocking): access widenings and interface injections of client classes on a
 *       dedicated server, which has none; they are not applied there, as under Fabric Loader
 *   <li>{@code POLY-COMPAT-135} (blocking): an interface injection (Fabric class tweaker {@code inject-interface})
 *       whose interface no installed jar has; the game class would fail to load
 * </ul>
 *
 * <p>Only references in the constant pool are checked; which code paths actually run is not analysed. Members
 * are checked for Minecraft classes, walking superclasses and interfaces as the JVM resolves them; interfaces that
 * installed mods inject into a game class ({@link #injectInterfaces}) count as its interfaces, as they will at run
 * time.
 */
public final class ModLinkVerifier implements AutoCloseable {

    private static final List<String> GAME_PACKAGES = List.of("net/minecraft/", "com/mojang/");
    /**
     * Packages under {@link #GAME_PACKAGES} that never were game classes: LaunchWrapper ({@code net.minecraft:launchwrapper})
     * was a separate library of the 1.6-1.12 launchers. Multi-version mods reference it from code for those versions;
     * a missing class there is noted like any other missing library ({@code POLY-COMPAT-113}).
     */
    private static final List<String> LIBRARY_PACKAGES = List.of("net/minecraft/launchwrapper/");

    private static final List<String> CLIENT_PACKAGES = List.of(
            "net/minecraft/client/", "net/minecraft/realms/", "com/mojang/blaze3d/", "com/mojang/realmsclient/");
    private static final List<String> JDK_PACKAGES = List.of("java/", "javax/", "jdk/");
    /**
     * API packages of other loaders. A class of a multi-loader jar that uses one of them, when nothing installed has
     * it, is that loader's code path: it fails as it loads, under Fabric Loader too, so it never runs. Minecraft
     * members it uses that this game lacks (methods the other loader patches into Minecraft) are noted, not blocking.
     */
    private static final List<String> OTHER_LOADER_PACKAGES = List.of("net/neoforged/", "net/minecraftforge/");

    private static final int EXAMPLES = 5;

    private final List<JarFile> gameJars = new ArrayList<>();
    private final Map<String, JarFile> gameClasses = new HashMap<>();
    private final ClassLoader platform;
    private final GameSide side;
    private final Map<String, Optional<ClassShape>> shapes = new HashMap<>();
    private final Map<Path, Set<String>> jarClasses = new HashMap<>();
    /** The jar each mod class seen so far comes from, for reading injected interfaces. */
    private final Map<String, Path> modClassJars = new HashMap<>();
    /** Classes of installed mods that do not run on this side ({@code environment}), such as client modules. */
    private final Set<String> absentOnThisSide = new HashSet<>();
    /** Interfaces installed mods inject, by game class. */
    private final Map<String, Set<String>> injectedInterfaces = new HashMap<>();

    /**
     * @param gameJarPaths the Minecraft jar (and any jar with game classes) of this launch
     * @param platform the class loader with the JDK, the game's libraries and Poly Mod Loader's own classes
     * @param side which game this is; client classes are absent from a dedicated server
     */
    public ModLinkVerifier(List<Path> gameJarPaths, ClassLoader platform, GameSide side) {
        this.platform = platform;
        this.side = side;
        try {
            for (Path path : gameJarPaths) {
                JarFile jar = new JarFile(path.toFile());
                gameJars.add(jar);
                jar.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .forEach(name -> gameClasses.putIfAbsent(name.substring(0, name.length() - 6), jar));
            }
        } catch (IOException e) {
            close();
            throw new UncheckedIOException("Cannot read the game jar for link checks", e);
        }
    }

    /**
     * Checks one mod jar.
     *
     * @param modJar the mod to check
     * @param otherModJars every other jar the game class loader will hold (installed mods of all loaders)
     * @param bridge the mod's loader bridge, which says which runtime packages are provided
     * @param entrypointClasses classes the mod's metadata names as entrypoints for this side
     */
    public List<CompatibilityFinding> verify(
            Path modJar, Collection<Path> otherModJars, ModLoaderBridge bridge, Collection<String> entrypointClasses) {
        return verify(modJar, otherModJars, bridge, entrypointClasses, List.of());
    }

    /**
     * Checks one mod jar and the access widenings it asks for.
     *
     * @param accessWidenings the mod's access widenings ({@link
     *     dev.polymodloader.compat.bridge.BridgeAssessment#accessWidenings()}); each class must be a game class and
     *     each member must be declared by that exact class, as the transform planner requires
     */
    public List<CompatibilityFinding> verify(
            Path modJar,
            Collection<Path> otherModJars,
            ModLoaderBridge bridge,
            Collection<String> entrypointClasses,
            List<BridgedAccessWidening> accessWidenings) {
        Set<String> modClasses = new HashSet<>(classesIn(modJar));
        for (Path other : otherModJars) {
            modClasses.addAll(classesIn(other));
        }
        Map<String, Set<String>> problems = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(modJar.toFile())) {
            for (JarEntry entry : jar.stream().toList()) {
                String name = entry.getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class")) {
                    continue;
                }
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                checkClass(name.substring(0, name.length() - 6), bytes, modClasses, bridge, problems);
            }
        } catch (IOException e) {
            problems.computeIfAbsent("POLY-COMPAT-118|classfile", key -> new TreeSet<>())
                    .add(String.valueOf(e));
        }
        for (String entrypoint : entrypointClasses) {
            // A Fabric entrypoint may name a member of the class: a.Main::init.
            String className =
                    entrypoint.contains("::") ? entrypoint.substring(0, entrypoint.indexOf("::")) : entrypoint;
            if (!modClasses.contains(className.replace('.', '/'))) {
                problems.computeIfAbsent("POLY-COMPAT-119|entrypoint", key -> new TreeSet<>())
                        .add(entrypoint);
            }
        }
        for (BridgedAccessWidening widening : accessWidenings) {
            checkAccessWidening(widening, problems);
        }
        return findings(problems);
    }

    /**
     * Records the jars of installed mods that do not run on this side because of their declared environment (Fabric
     * {@code environment}, POLY-COMPAT-108), such as Fabric API's client modules on a dedicated server. Under Fabric
     * Loader their classes are absent there too, and other mods reach them only from code for the other side, so a
     * reference to them is noted ({@code POLY-COMPAT-113}) instead of blocking.
     */
    public void absentOnThisSide(Collection<Path> jars) {
        jars.forEach(jar -> absentOnThisSide.addAll(classesIn(jar)));
    }

    /**
     * Records interfaces that installed mods inject into game classes, so that member references resolved through
     * them are found. Call before {@link #verify} with the injections of every mod that may load.
     */
    public void injectInterfaces(Collection<BridgedInterfaceInjection> injections) {
        for (BridgedInterfaceInjection injection : injections) {
            injectedInterfaces
                    .computeIfAbsent(injection.target(), target -> new java.util.LinkedHashSet<>())
                    .add(injection.interfaceName());
        }
        shapes.clear();
    }

    /**
     * Checks one mod jar, the access widenings it asks for and the interfaces it injects.
     *
     * @param injections the mod's interface injections ({@link
     *     dev.polymodloader.compat.bridge.BridgeAssessment#injectedInterfaces()}); each class must be a game class and
     *     each interface must be in the mod, another installed mod, or on Poly Mod Loader's class path
     */
    public List<CompatibilityFinding> verify(
            Path modJar,
            Collection<Path> otherModJars,
            ModLoaderBridge bridge,
            Collection<String> entrypointClasses,
            List<BridgedAccessWidening> accessWidenings,
            List<BridgedInterfaceInjection> injections) {
        List<CompatibilityFinding> findings =
                new ArrayList<>(verify(modJar, otherModJars, bridge, entrypointClasses, accessWidenings));
        Set<String> modClasses = new HashSet<>(classesIn(modJar));
        otherModJars.forEach(other -> modClasses.addAll(classesIn(other)));
        Map<String, Set<String>> problems = new LinkedHashMap<>();
        for (BridgedInterfaceInjection injection : injections) {
            if (!injection.appliedByBridge()) {
                continue;
            }
            String where = injection.describe() + " (" + injection.source() + ")";
            if (!gameClasses.containsKey(injection.target())) {
                boolean clientOnServer = side == GameSide.DEDICATED_SERVER && isClientClass(injection.target());
                problems.computeIfAbsent(
                                clientOnServer
                                        ? "POLY-COMPAT-130|client-injection"
                                        : "POLY-COMPAT-137|injection-target",
                                key -> new TreeSet<>())
                        .add(where);
            } else if (!modClasses.contains(injection.interfaceName()) && !platformHas(injection.interfaceName())) {
                problems.computeIfAbsent("POLY-COMPAT-135|injection-interface", key -> new TreeSet<>())
                        .add(where);
            }
        }
        findings.addAll(findings(problems));
        return findings;
    }

    /** Whether {@code className} (internal form) is in a package only the client jar has. */
    static boolean isClientClass(String className) {
        return CLIENT_PACKAGES.stream().anyMatch(className::startsWith);
    }

    private void checkAccessWidening(BridgedAccessWidening widening, Map<String, Set<String>> problems) {
        String owner = widening.owner();
        String where = widening.describeTarget() + " (" + widening.source() + ")";
        if (!gameClasses.containsKey(owner)) {
            if (side == GameSide.DEDICATED_SERVER && isClientClass(owner)) {
                problems.computeIfAbsent("POLY-COMPAT-130|client-widening", key -> new TreeSet<>())
                        .add(where);
            } else if (platformHas(owner)) {
                problems.computeIfAbsent("POLY-COMPAT-128|not-game", key -> new TreeSet<>())
                        .add(where);
            } else {
                problems.computeIfAbsent("POLY-COMPAT-137|widening", key -> new TreeSet<>())
                        .add(where);
            }
            return;
        }
        if (widening.kind() == BridgedAccessWidening.Kind.CLASS) {
            return;
        }
        Optional<ClassShape> shape = shape(owner);
        boolean field = widening.kind() == BridgedAccessWidening.Kind.FIELD;
        boolean declared = shape.isPresent()
                && (field ? shape.get().fields() : shape.get().methods())
                        .contains(widening.name() + widening.descriptor());
        if (!declared) {
            problems.computeIfAbsent("POLY-COMPAT-137|widening", key -> new TreeSet<>())
                    .add(where);
        }
    }

    private void checkClass(
            String className,
            byte[] bytes,
            Set<String> modClasses,
            ModLoaderBridge bridge,
            Map<String, Set<String>> problems) {
        ClassModel model;
        try {
            model = ClassFile.of().parse(bytes);
        } catch (IllegalArgumentException e) {
            problems.computeIfAbsent("POLY-COMPAT-118|classfile", key -> new TreeSet<>())
                    .add(className + " (" + e.getMessage() + ")");
            return;
        }
        Set<String> checkedClasses = new HashSet<>();
        try {
            boolean otherLoaderCode = usesAbsentOtherLoaderApi(model, modClasses);
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof ClassEntry classEntry) {
                    String referenced = elementType(classEntry.asInternalName());
                    if (referenced != null && checkedClasses.add(referenced)) {
                        checkClassReference(referenced, modClasses, bridge, problems);
                    }
                } else if (entry instanceof MemberRefEntry member) {
                    String owner = elementType(member.owner().asInternalName());
                    if (owner != null
                            && !modClasses.contains(owner)
                            && bridge.unsupportedMembers()
                                    .contains(owner + "." + member.name().stringValue())) {
                        problems.computeIfAbsent("POLY-COMPAT-111|unsupported", key -> new TreeSet<>())
                                .add(owner.replace('/', '.') + "#"
                                        + member.name().stringValue());
                    }
                    if (owner != null
                            && !modClasses.contains(owner)
                            && providedRuntimeClass(owner, bridge)
                            && !hasMember(
                                    owner,
                                    member.name().stringValue(),
                                    member.type().stringValue(),
                                    member instanceof FieldRefEntry)) {
                        problems.computeIfAbsent("POLY-COMPAT-111|runtime-member", key -> new TreeSet<>())
                                .add(owner.replace('/', '.')
                                        + (member instanceof FieldRefEntry ? "." : "#")
                                        + member.name().stringValue()
                                        + member.type().stringValue());
                    }
                    if (owner != null && gameClasses.containsKey(owner) && !modClasses.contains(owner)) {
                        boolean field = member instanceof FieldRefEntry;
                        String name = member.name().stringValue();
                        String descriptor = member.type().stringValue();
                        if (!hasMember(owner, name, descriptor, field)) {
                            // A member whose type names a class nothing installed has belongs to an optional
                            // integration (another loader's code path in a multi-loader jar): it fails only if run.
                            // So does any member a class of another loader's code path uses.
                            boolean optional = otherLoaderCode
                                    || descriptorClasses(descriptor).stream()
                                            .anyMatch(type -> !modClasses.contains(type)
                                                    && !gameClasses.containsKey(type)
                                                    && !platformHas(type));
                            problems.computeIfAbsent(
                                            optional ? "POLY-COMPAT-113|optional-member" : "POLY-COMPAT-112|member",
                                            key -> new TreeSet<>())
                                    .add(owner.replace('/', '.') + (field ? "." : "#") + name + descriptor);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            problems.computeIfAbsent("POLY-COMPAT-118|classfile", key -> new TreeSet<>())
                    .add(className + " (" + e.getMessage() + ")");
        }
    }

    /** Whether {@code model} uses a class of {@link #OTHER_LOADER_PACKAGES} that nothing installed has. */
    private boolean usesAbsentOtherLoaderApi(ClassModel model, Set<String> modClasses) {
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof ClassEntry classEntry) {
                String referenced = elementType(classEntry.asInternalName());
                if (referenced != null
                        && OTHER_LOADER_PACKAGES.stream().anyMatch(referenced::startsWith)
                        && !modClasses.contains(referenced)
                        && !platformHas(referenced)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void checkClassReference(
            String referenced, Set<String> modClasses, ModLoaderBridge bridge, Map<String, Set<String>> problems) {
        if (modClasses.contains(referenced)) {
            return;
        }
        boolean runtimePackage = bridge.runtimePackages().stream().anyMatch(referenced::startsWith);
        boolean provided = bridge.providedPackages().stream().anyMatch(referenced::startsWith);
        // A loader runtime class must be both provided and actually present; otherwise it fails when used.
        if (runtimePackage && !(provided && platformHas(referenced))) {
            problems.computeIfAbsent(
                            absentOnThisSide.contains(referenced)
                                    ? "POLY-COMPAT-113|other-side"
                                    : "POLY-COMPAT-111|runtime",
                            key -> new TreeSet<>())
                    .add(referenced.replace('/', '.'));
            return;
        }
        if (gameClasses.containsKey(referenced) || platformHas(referenced)) {
            return;
        }
        if (referenced.startsWith("jdk/incubator/")) {
            problems.computeIfAbsent("POLY-COMPAT-113|incubator", key -> new TreeSet<>())
                    .add(referenced.replace('/', '.'));
            return;
        }
        if (JDK_PACKAGES.stream().anyMatch(referenced::startsWith)) {
            problems.computeIfAbsent("POLY-COMPAT-118|jdk", key -> new TreeSet<>())
                    .add(referenced.replace('/', '.'));
            return;
        }
        boolean game = GAME_PACKAGES.stream().anyMatch(referenced::startsWith)
                && LIBRARY_PACKAGES.stream().noneMatch(referenced::startsWith);
        boolean client = CLIENT_PACKAGES.stream().anyMatch(referenced::startsWith);
        if (game && !(client && side == GameSide.DEDICATED_SERVER)) {
            problems.computeIfAbsent("POLY-COMPAT-112|class", key -> new TreeSet<>())
                    .add(referenced.replace('/', '.'));
        } else if (client && side == GameSide.DEDICATED_SERVER) {
            problems.computeIfAbsent("POLY-COMPAT-113|client", key -> new TreeSet<>())
                    .add(referenced.replace('/', '.'));
        } else {
            problems.computeIfAbsent("POLY-COMPAT-113|missing", key -> new TreeSet<>())
                    .add(referenced.replace('/', '.'));
        }
    }

    private static List<CompatibilityFinding> findings(Map<String, Set<String>> problems) {
        List<CompatibilityFinding> findings = new ArrayList<>();
        for (Map.Entry<String, Set<String>> problem : problems.entrySet()) {
            String[] key = problem.getKey().split("\\|");
            String code = key[0];
            Set<String> items = problem.getValue();
            String examples = String.join(", ", items.stream().limit(EXAMPLES).toList())
                    + (items.size() > EXAMPLES ? ", and " + (items.size() - EXAMPLES) + " more" : "");
            String detail = switch (key[1]) {
                case "unsupported" ->
                    items.size() + " method(s) of the loader's API that Poly Mod Loader does not implement: "
                            + examples;
                case "runtime-member" ->
                    items.size()
                            + " method(s) or field(s) of the loader's runtime that Poly Mod Loader does not provide: "
                            + examples;
                case "runtime" ->
                    items.size() + " class(es) of the loader's runtime that Poly Mod Loader does not" + " provide: "
                            + examples;
                case "class" ->
                    items.size() + " Minecraft class(es) this game version does not have (the mod"
                            + " was built for another version): " + examples;
                case "member" ->
                    items.size() + " Minecraft method(s) or field(s) this game version does not"
                            + " have (the mod was built for another version): " + examples;
                case "optional-member" ->
                    items.size() + " Minecraft method(s) or field(s) whose types no installed jar has, usually another"
                            + " loader's code path; fine if it never runs: " + examples;
                case "other-side" ->
                    items.size() + " class(es) of mods that do not run on this side, as under Fabric Loader; fine if"
                            + " only code for the other side uses them: " + examples;
                case "client" ->
                    items.size() + " client class(es) referenced, absent on a dedicated server;"
                            + " fine if only client code uses them: " + examples;
                case "missing" ->
                    items.size() + " class(es) no installed mod or library has, usually an" + " optional integration: "
                            + examples;
                case "classfile" -> "class files this Java runtime cannot read: " + examples;
                case "jdk" -> items.size() + " JDK class(es) this Java runtime does not have: " + examples;
                case "entrypoint" -> "entrypoint class(es) not found in any installed jar: " + examples;
                case "widening" ->
                    items.size() + " access widening target(s) this game version does not have; not applied, as"
                            + " Fabric Loader applies such lines to nothing: " + examples;
                case "incubator" ->
                    items.size() + " JDK incubator class(es), present only when their module is enabled; fine if"
                            + " the mod checks first: " + examples;
                case "not-game" ->
                    items.size() + " access widening target(s) outside the game jar; Poly Mod Loader widens only"
                            + " game classes: " + examples;
                case "client-widening" ->
                    items.size() + " access widening(s) of client classes, absent on a dedicated server and"
                            + " not applied: " + examples;
                case "client-injection" ->
                    items.size() + " interface injection(s) into client classes, absent on a dedicated server and"
                            + " not applied: " + examples;
                case "injection-target" ->
                    items.size() + " interface injection target(s) this game version does not have; not applied,"
                            + " as Fabric Loader applies such lines to nothing: " + examples;
                case "injection-interface" ->
                    items.size() + " injected interface(s) that no installed jar has; the game class would fail to"
                            + " load: " + examples;
                default -> examples;
            };
            boolean blocking = !code.equals("POLY-COMPAT-113")
                    && !code.equals("POLY-COMPAT-130")
                    && !code.equals("POLY-COMPAT-137");
            findings.add(new CompatibilityFinding(code, blocking, "link check", detail));
        }
        return findings;
    }

    /**
     * Resolves a field or method as the JVM does: the class, its superclasses, then all superinterfaces; a constructor
     * only in the class itself. Interfaces mods add to a class count, when they can be read.
     */
    private boolean hasMember(String owner, String name, String descriptor, boolean field) {
        if (!field && name.equals("<init>")) {
            Optional<ClassShape> shape = shape(owner);
            return shape.isEmpty() || shape.get().methods().contains(name + descriptor);
        }
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            Optional<ClassShape> shape = shape(current);
            if (shape.isEmpty()) {
                // A supertype that cannot be read here: assume it declares the member rather than block.
                return true;
            }
            Set<String> members = field ? shape.get().fields() : shape.get().methods();
            if (members.contains(name + descriptor)) {
                return true;
            }
            shape.get().superclass().ifPresent(queue::add);
            queue.addAll(shape.get().interfaces());
            // An added interface that cannot be read (from a mod that is not installed or not loaded) is skipped, so
            // that it cannot hide a missing member.
            injectedInterfaces.getOrDefault(current, Set.of()).stream()
                    .filter(added -> shape(added).isPresent())
                    .forEach(queue::add);
            if (shape.get().isInterface() && !field) {
                queue.add("java/lang/Object");
            }
        }
        return false;
    }

    /**
     * The shape of a class of the game, the platform or a mod jar seen so far. A class that cannot be found yet is not
     * remembered as missing, because its mod jar may be seen by a later check.
     */
    private Optional<ClassShape> shape(String className) {
        Optional<ClassShape> known = shapes.get(className);
        if (known != null) {
            return known;
        }
        byte[] bytes = classBytes(className);
        Optional<ClassShape> shape;
        try {
            shape = bytes == null
                    ? Optional.empty()
                    : Optional.of(ClassShape.of(ClassFile.of().parse(bytes)));
        } catch (IllegalArgumentException e) {
            shape = Optional.empty();
        }
        if (shape.isPresent() || gameClasses.containsKey(className)) {
            shapes.put(className, shape);
        }
        return shape;
    }

    private byte[] classBytes(String className) {
        try {
            JarFile jar = gameClasses.get(className);
            if (jar != null) {
                try (InputStream input = jar.getInputStream(jar.getEntry(className + ".class"))) {
                    return input.readAllBytes();
                }
            }
            try (InputStream input = platform.getResourceAsStream(className + ".class")) {
                if (input != null) {
                    return input.readAllBytes();
                }
            }
            Path modJar = modClassJars.get(className);
            if (modJar == null) {
                return null;
            }
            try (JarFile modFile = new JarFile(modJar.toFile())) {
                JarEntry entry = modFile.getJarEntry(className + ".class");
                if (entry == null) {
                    return null;
                }
                try (InputStream input = modFile.getInputStream(entry)) {
                    return input.readAllBytes();
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    /** A class of the loader's runtime that Poly Mod Loader provides and has on its class path. */
    private boolean providedRuntimeClass(String className, ModLoaderBridge bridge) {
        return bridge.runtimePackages().stream().anyMatch(className::startsWith)
                && bridge.providedPackages().stream().anyMatch(className::startsWith)
                && platformHas(className);
    }

    private boolean platformHas(String className) {
        return platform.getResource(className + ".class") != null;
    }

    private Set<String> classesIn(Path jarPath) {
        return jarClasses.computeIfAbsent(jarPath, path -> {
            Set<String> names = new HashSet<>();
            try (JarFile jar = new JarFile(path.toFile())) {
                jar.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .forEach(name -> {
                            String className = stripVersionPrefix(name.substring(0, name.length() - 6));
                            names.add(className);
                            if (!name.startsWith("META-INF/")) {
                                modClassJars.putIfAbsent(className, path);
                            }
                        });
            } catch (IOException e) {
                // An unreadable jar provides nothing; its own assessment reports it.
            }
            return names;
        });
    }

    /** {@code META-INF/versions/21/a/B} is class {@code a/B} in a multi-release jar. */
    private static String stripVersionPrefix(String name) {
        if (name.startsWith("META-INF/versions/")) {
            int slash = name.indexOf('/', "META-INF/versions/".length());
            return slash < 0 ? name : name.substring(slash + 1);
        }
        return name;
    }

    /** The classes a field or method descriptor names, in internal form. */
    private static List<String> descriptorClasses(String descriptor) {
        List<String> classes = new ArrayList<>();
        int index = 0;
        while ((index = descriptor.indexOf('L', index)) >= 0) {
            int end = descriptor.indexOf(';', index);
            if (end < 0) {
                break;
            }
            classes.add(descriptor.substring(index + 1, end));
            index = end + 1;
        }
        return classes;
    }

    /** The class an array descriptor holds, or {@code null} for arrays of primitives. */
    private static String elementType(String internalName) {
        if (!internalName.startsWith("[")) {
            return internalName;
        }
        String element = internalName.replaceFirst("^\\[+", "");
        return element.startsWith("L") && element.endsWith(";") ? element.substring(1, element.length() - 1) : null;
    }

    @Override
    public void close() {
        for (JarFile jar : gameJars) {
            try {
                jar.close();
            } catch (IOException e) {
                // Closing a read-only jar cannot lose data; nothing else to do.
            }
        }
    }

    /** The parts of a class that member resolution needs. */
    private record ClassShape(
            Optional<String> superclass,
            List<String> interfaces,
            boolean isInterface,
            Set<String> methods,
            Set<String> fields) {

        static ClassShape of(ClassModel model) {
            Set<String> methods = new HashSet<>();
            model.methods()
                    .forEach(method -> methods.add(method.methodName().stringValue()
                            + method.methodType().stringValue()));
            Set<String> fields = new HashSet<>();
            model.fields()
                    .forEach(field -> fields.add(
                            field.fieldName().stringValue() + field.fieldType().stringValue()));
            return new ClassShape(
                    model.superclass().map(ClassEntry::asInternalName),
                    model.interfaces().stream().map(ClassEntry::asInternalName).toList(),
                    (model.flags().flagsMask() & ClassFile.ACC_INTERFACE) != 0,
                    methods,
                    fields);
        }
    }
}
