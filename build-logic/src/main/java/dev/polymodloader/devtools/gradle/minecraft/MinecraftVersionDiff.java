package dev.polymodloader.devtools.gradle.minecraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Compares two Minecraft versions for what one supported version's modules use (ADR-0011): the game
 * classes, fields and methods that {@code poly-version-adapter-v<id>} and {@code poly-game-v<id>} refer to,
 * and whether the other version's client jar still has them. It also lists what changed in the version
 * JSON (Java version, libraries, JVM arguments), which the launch and the installer depend on.
 *
 * <p>This is the first check before adding a version with {@code newMinecraftVersion}, or before listing a
 * version as a hotfix of a supported one. It does not replace compiling and testing on the new version:
 * constants that {@code javac} inlined leave no reference behind, and a transformation rule's member target
 * is a string the scan sees only as text, not as a member (its owner class is found when written as a class
 * name). The rule targets are checked by the game layer's {@code BuiltInRulesTest}.
 */
final class MinecraftVersionDiff {

    /** A class name inside a descriptor or signature, such as {@code Lnet/minecraft/world/Level;}. */
    private static final Pattern DESCRIPTOR_CLASS = Pattern.compile("L([^;<>:\\[]+)[;<]");

    /** How a member reference resolves in one jar. */
    enum Resolution {
        /** The owner or one of its supertypes in the jar or the JDK declares the member. */
        FOUND,
        /** The owner class is not in the jar. */
        NO_CLASS,
        /** Not declared in the jar or the JDK, and a supertype comes from a library, which may declare it. */
        OUTSIDE_JAR,
        /** The whole hierarchy is in the jar or the JDK and none of it declares the member. */
        MISSING
    }

    /** A field or method the modules refer to. */
    record MemberRef(String owner, String name, String descriptor, boolean method) {

        String display() {
            return owner.replace('/', '.') + (method ? "." : "#") + name + " " + descriptor;
        }
    }

    /** A member whose resolution got worse from one version to the other. */
    record MemberChange(MemberRef member, Resolution from, Resolution to) {}

    /**
     * What resolving members needs from one class, with Mojang names (translated through the mappings for an
     * obfuscated jar).
     *
     * @param superName the superclass, or null for {@code java/lang/Object}
     * @param methods {@code name + descriptor} of each declared method
     * @param fields {@code name + descriptor} of each declared field
     */
    record ClassInfo(String superName, List<String> interfaces, Set<String> methods, Set<String> fields) {}

    /** One library whose version changed, or that was added or removed (null on the missing side). */
    record LibraryChange(String library, String fromVersion, String toVersion) {}

    /**
     * The comparison.
     *
     * @param usedClasses every game class the modules name
     * @param missingClasses used classes the other jar does not have
     * @param toObfuscated the other version is obfuscated and was read through Mojang's mappings; its class
     *     bytes cannot be compared, so {@code changedClasses} is empty and {@code changedClassCount} is -1
     * @param changedClasses used classes whose bytes differ between the jars
     * @param memberChanges used members that no longer resolve in the other jar
     */
    record Report(
            String fromVersion,
            String toVersion,
            boolean toObfuscated,
            int fromJava,
            int toJava,
            int fromClassCount,
            int toClassCount,
            int addedClassCount,
            int removedClassCount,
            int changedClassCount,
            int usedMemberCount,
            Set<String> usedClasses,
            Set<String> missingClasses,
            Set<String> changedClasses,
            List<MemberChange> memberChanges,
            List<LibraryChange> libraryChanges,
            List<String> removedJvmArguments,
            List<String> addedJvmArguments) {

        /** True if nothing the modules use moved: the other version may run on these modules as a hotfix. */
        boolean hotfixCandidate() {
            return !toObfuscated
                    && fromJava == toJava
                    && missingClasses.isEmpty()
                    && changedClasses.isEmpty()
                    && memberChanges.isEmpty();
        }
    }

    private MinecraftVersionDiff() {}

    /**
     * Compares the two versions.
     *
     * @param fromJson the version JSON of the supported version
     * @param fromJar its client jar
     * @param toJson the version JSON of the other version
     * @param toJar its client jar
     * @param toMappings Mojang's mappings when the other version is obfuscated, otherwise null
     * @param moduleClasses directories or jars with the compiled classes of the supported version's modules
     */
    static Report compare(
            String fromVersion,
            JsonObject fromJson,
            Path fromJar,
            String toVersion,
            JsonObject toJson,
            Path toJar,
            MojangMappings toMappings,
            Collection<Path> moduleClasses) {
        Map<String, byte[]> from = readClasses(fromJar);
        Map<String, byte[]> toBytes = readClasses(toJar);
        Map<String, ClassInfo> fromInfo = classInfo(from, null);
        Map<String, ClassInfo> toInfo = classInfo(toBytes, toMappings);
        boolean obfuscated = toMappings != null;

        Set<String> usedClasses = new TreeSet<>();
        Set<MemberRef> usedMembers = new TreeSet<>(Comparator.comparing(MemberRef::owner)
                .thenComparing(MemberRef::name)
                .thenComparing(MemberRef::descriptor));
        for (byte[] bytes : moduleClassBytes(moduleClasses)) {
            collectUses(ClassFile.of().parse(bytes), from.keySet(), usedClasses, usedMembers);
        }
        usedMembers.forEach(member -> usedClasses.add(member.owner()));

        Set<String> missingClasses = new TreeSet<>();
        Set<String> changedClasses = new TreeSet<>();
        for (String name : usedClasses) {
            if (!toInfo.containsKey(name)) {
                missingClasses.add(name);
            } else if (!obfuscated && !Arrays.equals(from.get(name), toBytes.get(name))) {
                changedClasses.add(name);
            }
        }

        Hierarchy fromHierarchy = new Hierarchy(fromInfo);
        Hierarchy toHierarchy = new Hierarchy(toInfo);
        List<MemberChange> memberChanges = new ArrayList<>();
        for (MemberRef member : usedMembers) {
            Resolution before = fromHierarchy.resolve(member);
            Resolution after = toHierarchy.resolve(member);
            if (after != Resolution.FOUND && before != after) {
                memberChanges.add(new MemberChange(member, before, after));
            }
        }

        int added = 0;
        int changed = obfuscated ? -1 : 0;
        for (String name : toInfo.keySet()) {
            byte[] before = from.get(name);
            if (before == null) {
                added++;
            } else if (!obfuscated && !Arrays.equals(before, toBytes.get(name))) {
                changed++;
            }
        }
        int removed = (int) from.keySet().stream().filter(name -> !toInfo.containsKey(name)).count();

        List<String> fromArguments = jvmArguments(fromJson);
        List<String> toArguments = jvmArguments(toJson);
        return new Report(
                fromVersion,
                toVersion,
                obfuscated,
                javaVersion(fromJson),
                javaVersion(toJson),
                from.size(),
                toInfo.size(),
                added,
                removed,
                changed,
                usedMembers.size(),
                usedClasses,
                missingClasses,
                changedClasses,
                memberChanges,
                libraryChanges(fromJson, toJson),
                fromArguments.stream().filter(argument -> !toArguments.contains(argument)).toList(),
                toArguments.stream().filter(argument -> !fromArguments.contains(argument)).toList());
    }

    /** Adds the game classes and members one module class names. */
    static void collectUses(
            ClassModel model, Set<String> gameClasses, Set<String> usedClasses, Set<MemberRef> usedMembers) {
        for (PoolEntry entry : model.constantPool()) {
            switch (entry) {
                case MemberRefEntry ref -> {
                    String owner = elementClass(ref.owner().asInternalName());
                    if (gameClasses.contains(owner)) {
                        usedMembers.add(new MemberRef(
                                owner, ref.name().stringValue(), ref.type().stringValue(), !(ref instanceof FieldRefEntry)));
                    }
                }
                case ClassEntry classEntry -> addIfGame(elementClass(classEntry.asInternalName()), gameClasses, usedClasses);
                case Utf8Entry text -> {
                    // Descriptors and signatures, and class names written as text (reflection, transformation rules).
                    String value = text.stringValue();
                    Matcher matcher = DESCRIPTOR_CLASS.matcher(value);
                    while (matcher.find()) {
                        addIfGame(matcher.group(1), gameClasses, usedClasses);
                    }
                    addIfGame(value.replace('.', '/'), gameClasses, usedClasses);
                }
                default -> {}
            }
        }
    }

    private static void addIfGame(String name, Set<String> gameClasses, Set<String> usedClasses) {
        if (gameClasses.contains(name)) {
            usedClasses.add(name);
        }
    }

    /** {@code [Lnet/minecraft/A;} and {@code [[I} name arrays; the element class is what must exist. */
    private static String elementClass(String internalName) {
        if (!internalName.startsWith("[")) {
            return internalName;
        }
        String element = internalName.replaceFirst("^\\[+", "");
        return element.startsWith("L") && element.endsWith(";") ? element.substring(1, element.length() - 1) : element;
    }

    /**
     * The class information of a jar by Mojang name. With mappings, every class, supertype, member name and
     * descriptor is translated from the obfuscated names the jar uses.
     */
    static Map<String, ClassInfo> classInfo(Map<String, byte[]> classes, MojangMappings mappings) {
        Map<String, ClassInfo> result = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            ClassModel model = ClassFile.of().parse(entry.getValue());
            String owner = entry.getKey();
            if (mappings == null) {
                result.put(owner, info(model));
                continue;
            }
            Set<String> methods = new HashSet<>();
            model.methods().forEach(method -> {
                String descriptor = method.methodType().stringValue();
                methods.add(mappings.methodName(owner, method.methodName().stringValue(), descriptor)
                        + mappings.descriptor(descriptor));
            });
            Set<String> fields = new HashSet<>();
            model.fields().forEach(field -> fields.add(mappings.fieldName(owner, field.fieldName().stringValue())
                    + mappings.descriptor(field.fieldType().stringValue())));
            result.put(
                    mappings.className(owner),
                    new ClassInfo(
                            model.superclass()
                                    .map(type -> mappings.className(type.asInternalName()))
                                    .orElse(null),
                            model.interfaces().stream()
                                    .map(type -> mappings.className(type.asInternalName()))
                                    .toList(),
                            methods,
                            fields));
        }
        return result;
    }

    private static ClassInfo info(ClassModel model) {
        Set<String> methods = new HashSet<>();
        model.methods().forEach(method -> methods.add(
                method.methodName().stringValue() + method.methodType().stringValue()));
        Set<String> fields = new HashSet<>();
        model.fields().forEach(field -> fields.add(field.fieldName().stringValue() + field.fieldType().stringValue()));
        return new ClassInfo(
                model.superclass().map(ClassEntry::asInternalName).orElse(null),
                model.interfaces().stream().map(ClassEntry::asInternalName).toList(),
                methods,
                fields);
    }

    /**
     * Walks supertypes in one jar, and in the classes outside it (the JDK both versions run on, and libraries
     * if the loader has them), to find where a member is declared.
     */
    static final class Hierarchy {

        private final Map<String, ClassInfo> classes;
        private final ClassLoader outside;
        private final Map<String, ClassInfo> outsideClasses = new HashMap<>();

        Hierarchy(Map<String, ClassInfo> classes) {
            this(classes, ClassLoader.getPlatformClassLoader());
        }

        /** With {@code outside} for every class not in the jar. */
        Hierarchy(Map<String, ClassInfo> classes, ClassLoader outside) {
            this.classes = classes;
            this.outside = outside;
        }

        Resolution resolve(MemberRef member) {
            if (!classes.containsKey(member.owner())) {
                return Resolution.NO_CLASS;
            }
            String key = member.name() + member.descriptor();
            boolean leavesJar = false;
            Deque<String> pending = new ArrayDeque<>(List.of(member.owner()));
            Set<String> seen = new HashSet<>();
            while (!pending.isEmpty()) {
                String name = pending.removeFirst();
                if (!seen.add(name)) {
                    continue;
                }
                ClassInfo info = info(name);
                if (info == null) {
                    leavesJar = true;
                    continue;
                }
                if ((member.method() ? info.methods() : info.fields()).contains(key)) {
                    return Resolution.FOUND;
                }
                if (info.superName() != null) {
                    pending.addLast(info.superName());
                }
                pending.addAll(info.interfaces());
            }
            return leavesJar ? Resolution.OUTSIDE_JAR : Resolution.MISSING;
        }

        /** A class of the jar, or one the outside loader has; null for any other class. */
        private ClassInfo info(String name) {
            ClassInfo known = classes.get(name);
            if (known != null) {
                return known;
            }
            if (outsideClasses.containsKey(name)) {
                return outsideClasses.get(name);
            }
            byte[] bytes = outsideClass(name);
            ClassInfo info = bytes == null ? null : MinecraftVersionDiff.info(ClassFile.of().parse(bytes));
            outsideClasses.put(name, info);
            return info;
        }

        private byte[] outsideClass(String name) {
            try (InputStream input = outside.getResourceAsStream(name + ".class")) {
                return input == null ? null : input.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read the class " + name, e);
            }
        }
    }

    /** Every class file in a jar, by internal name. */
    static Map<String, byte[]> readClasses(Path jar) {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            for (JarEntry entry : file.stream().toList()) {
                String name = entry.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class")) {
                    try (InputStream input = file.getInputStream(entry)) {
                        classes.put(name.substring(0, name.length() - ".class".length()), input.readAllBytes());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + jar, e);
        }
        return classes;
    }

    private static List<byte[]> moduleClassBytes(Collection<Path> locations) {
        List<byte[]> result = new ArrayList<>();
        for (Path location : locations) {
            if (Files.isDirectory(location)) {
                try (Stream<Path> files = Files.walk(location)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".class"))
                            .sorted()
                            .toList()) {
                        result.add(Files.readAllBytes(file));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read " + location, e);
                }
            } else if (Files.isRegularFile(location)) {
                result.addAll(readClasses(location).values());
            } else {
                throw new IllegalStateException("Module classes " + location + " do not exist; compile the modules first.");
            }
        }
        return result;
    }

    private static int javaVersion(JsonObject version) {
        JsonObject java = version.getAsJsonObject("javaVersion");
        return java == null ? 0 : java.get("majorVersion").getAsInt();
    }

    /** The plain JVM arguments of the version JSON; rule-guarded entries are written as their value. */
    static List<String> jvmArguments(JsonObject version) {
        JsonObject arguments = version.getAsJsonObject("arguments");
        if (arguments == null || !arguments.has("jvm")) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement element : arguments.getAsJsonArray("jvm")) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            } else {
                JsonElement value = element.getAsJsonObject().get("value");
                String rules = element.getAsJsonObject().get("rules").toString();
                result.add((value.isJsonArray() ? value.toString() : value.getAsString()) + " (rules " + rules + ")");
            }
        }
        return result;
    }

    /**
     * Library changes by {@code group:artifact}, ignoring native classifiers (which follow their library's
     * version); a classifier that is not for natives stays part of the key.
     */
    static List<LibraryChange> libraryChanges(JsonObject fromJson, JsonObject toJson) {
        Map<String, String> from = libraries(fromJson);
        Map<String, String> to = libraries(toJson);
        Set<String> names = new TreeSet<>(from.keySet());
        names.addAll(to.keySet());
        List<LibraryChange> changes = new ArrayList<>();
        for (String name : names) {
            if (!Objects.equals(from.get(name), to.get(name))) {
                changes.add(new LibraryChange(name, from.get(name), to.get(name)));
            }
        }
        return changes;
    }

    private static Map<String, String> libraries(JsonObject version) {
        Map<String, String> result = new TreeMap<>();
        for (JsonElement element : version.getAsJsonArray("libraries")) {
            String[] parts = element.getAsJsonObject().get("name").getAsString().split(":");
            if (parts.length < 3) {
                continue;
            }
            String key = parts[0] + ":" + parts[1];
            if (parts.length > 3 && !parts[3].startsWith("natives-")) {
                key += ":" + parts[3];
            }
            result.merge(key, parts[2], (left, right) -> left.equals(right) ? left : left + ", " + right);
        }
        return result;
    }

    /** The report as Markdown. */
    static String markdown(Report report) {
        StringBuilder out = new StringBuilder();
        String from = report.fromVersion();
        String to = report.toVersion();
        out.append("# Minecraft ").append(from).append(" → ").append(to).append("\n\n");
        out.append("What the modules of Minecraft ")
                .append(from)
                .append(" (")
                .append(MinecraftVersions.adapterModule(from))
                .append(", ")
                .append(MinecraftVersions.gameLayerModule(from))
                .append(") use, checked against the client jar of ")
                .append(to)
                .append(". Constants inlined by javac and the member targets of transformation rules are not"
                        + " checked here; compile and test on the new version (docs/contributing/"
                        + "new-minecraft-version.md).\n\n");
        if (report.toObfuscated()) {
            out.append(to)
                    .append(" is obfuscated: its jar was read through Mojang's official mappings, so every name here"
                            + " is a Mojang name. Class bytes cannot be compared across the remap, so changed classes"
                            + " are not listed (ADR-0019).\n\n");
        }
        out.append("## Verdict\n\n");
        if (report.hotfixCandidate()) {
            out.append("Nothing the modules use changed. ")
                    .append(to)
                    .append(" may run on the ")
                    .append(from)
                    .append(" modules as a hotfix, after the remaining checks of the hotfix section.\n\n");
        } else if (report.toObfuscated()) {
            out.append(to)
                    .append(" needs a remapped game jar and its own modules (ADR-0019; newMinecraftVersion -Pfrom=")
                    .append(from)
                    .append(" -Pto=")
                    .append(to)
                    .append(" -Pjava=25, as every version runs on Java 25): ")
                    .append(report.missingClasses().size())
                    .append(" used classes missing, ")
                    .append(report.memberChanges().size())
                    .append(" used members no longer found.\n\n");
        } else {
            out.append(to)
                    .append(" needs its own modules (newMinecraftVersion -Pfrom=")
                    .append(from)
                    .append(" -Pto=")
                    .append(to)
                    .append(report.fromJava() != report.toJava() ? " -Pjava=" + report.toJava() : "")
                    .append("): ")
                    .append(report.missingClasses().size())
                    .append(" used classes missing, ")
                    .append(report.memberChanges().size())
                    .append(" used members no longer found, ")
                    .append(report.changedClasses().size())
                    .append(" used classes changed.\n\n");
        }
        out.append("## Summary\n\n| | ").append(from).append(" | ").append(to).append(" |\n|---|---|---|\n");
        out.append("| Java | ")
                .append(report.fromJava())
                .append(" | ")
                .append(report.toJava())
                .append(" |\n");
        out.append("| Classes in the client jar | ")
                .append(report.fromClassCount())
                .append(" | ")
                .append(report.toClassCount())
                .append(" |\n\n");
        out.append("- Client jar: ")
                .append(report.addedClassCount())
                .append(" classes added, ")
                .append(report.removedClassCount())
                .append(" removed, ")
                .append(report.toObfuscated() ? "changes not compared" : report.changedClassCount() + " changed")
                .append(".\n");
        out.append("- The modules use ")
                .append(report.usedClasses().size())
                .append(" game classes and ")
                .append(report.usedMemberCount())
                .append(" game fields and methods.\n\n");
        section(out, "Used classes missing in " + to, report.missingClasses().stream()
                .map(name -> "`" + name.replace('/', '.') + "`")
                .toList());
        section(out, "Used members no longer found in " + to, report.memberChanges().stream()
                .map(change -> "`" + change.member().display() + "`: " + describe(change.to()))
                .toList());
        if (!report.toObfuscated()) {
            section(out, "Used classes that changed", report.changedClasses().stream()
                    .map(name -> "`" + name.replace('/', '.') + "`")
                    .toList());
        }
        section(out, "Library changes (version JSON)", report.libraryChanges().stream()
                .map(change -> "`" + change.library() + "`: " + (change.fromVersion() == null ? "added" : change.fromVersion())
                        + " → " + (change.toVersion() == null ? "removed" : change.toVersion()))
                .toList());
        List<String> arguments = new ArrayList<>();
        report.removedJvmArguments().forEach(argument -> arguments.add("removed `" + argument + "`"));
        report.addedJvmArguments().forEach(argument -> arguments.add("added `" + argument + "`"));
        section(out, "JVM argument changes (version JSON)", arguments);
        return out.toString();
    }

    private static String describe(Resolution resolution) {
        return switch (resolution) {
            case FOUND -> "found";
            case NO_CLASS -> "its class is gone";
            case OUTSIDE_JAR -> "not declared in the jar any more; a library supertype may declare it";
            case MISSING -> "not declared by the class or its supertypes";
        };
    }

    private static void section(StringBuilder out, String title, List<String> lines) {
        out.append("## ").append(title).append(" (").append(lines.size()).append(")\n\n");
        if (lines.isEmpty()) {
            out.append("None.\n\n");
            return;
        }
        lines.forEach(line -> out.append("- ").append(line).append('\n'));
        out.append('\n');
    }
}
