package dev.polymodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The self-check of a jar remapped by {@link MojangJarRemapper} (ADR-0019, "A remap self-check"). It reads
 * the remapped classes only; loading and linking them is {@link RemappedJarLoadCheck}, in its own JVM.
 *
 * <p>It finds:
 *
 * <ul>
 *   <li>classes left in the default package that the mappings do not map to themselves (a class the mappings
 *       missed, or a reference to one);
 *   <li>a class that declares two fields or two methods with the same name and descriptor after the remap;
 *   <li>a field or method reference to a game class that no class in its hierarchy declares (a member renamed
 *       in one place but not another, such as an inherited member reached through a subclass).
 * </ul>
 */
final class RemappedJarCheck {

    /** At most this many examples are kept per kind of problem. */
    private static final int EXAMPLES = 20;

    /**
     * The result.
     *
     * @param classes the internal names of the remapped classes
     * @param classCount classes in the remapped jar
     * @param defaultPackageClasses classes or references left in the default package that the mappings do not
     *     map to themselves
     * @param duplicateMembers {@code class.name descriptor} of members declared twice
     * @param referenceCount field and method references to game classes that were resolved
     * @param unresolvedReferences the first of the references no class in the hierarchy declares
     * @param unresolvedReferenceCount how many references no class in the hierarchy declares
     * @param outsideReferences references whose hierarchy leaves the jar into a class the libraries do not
     *     have, so they could not be decided
     */
    record Result(
            Set<String> classes,
            int classCount,
            Set<String> defaultPackageClasses,
            Set<String> duplicateMembers,
            int referenceCount,
            List<String> unresolvedReferences,
            int unresolvedReferenceCount,
            int outsideReferences) {

        boolean passed() {
            return defaultPackageClasses.isEmpty() && duplicateMembers.isEmpty() && unresolvedReferenceCount == 0;
        }
    }

    /**
     * Whether a class that a remapped class needs but no loader has is a library class, which the remap cannot
     * have caused: not in the jar, not a Mojang name of the mappings and not in the default package. Such a
     * class comes from a library of another OS (such as {@code ca.weblite.objc} on macOS), which this machine
     * does not download.
     */
    static boolean isLibraryClass(String internalName, Set<String> jarClasses, MojangMappings mappings) {
        return internalName.contains("/") && !jarClasses.contains(internalName) && !mappings.namesClass(internalName);
    }

    private RemappedJarCheck() {}

    static Result check(Path remappedJar, MojangMappings mappings, Collection<Path> libraries) {
        Map<String, byte[]> classes = MinecraftVersionDiff.readClasses(remappedJar);
        Set<String> identity = new HashSet<>();
        for (String obfuscated : mappings.obfuscatedClasses()) {
            if (mappings.className(obfuscated).equals(obfuscated)) {
                identity.add(obfuscated);
            }
        }
        Set<String> defaultPackage = new TreeSet<>();
        Set<String> duplicates = new TreeSet<>();
        List<ClassModel> models = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            ClassModel model = ClassFile.of().parse(entry.getValue());
            models.add(model);
            checkDefaultPackage(entry.getKey(), identity, defaultPackage);
            Set<String> methods = new HashSet<>();
            model.methods().forEach(method -> {
                String key = method.methodName().stringValue() + " " + method.methodType().stringValue();
                if (!methods.add(key)) {
                    duplicates.add(entry.getKey() + "." + key);
                }
            });
            Set<String> fields = new HashSet<>();
            model.fields().forEach(field -> {
                String key = field.fieldName().stringValue() + " " + field.fieldType().stringValue();
                if (!fields.add(key)) {
                    duplicates.add(entry.getKey() + "#" + key);
                }
            });
        }

        Map<String, MinecraftVersionDiff.ClassInfo> info = MinecraftVersionDiff.classInfo(classes, null);
        int references = 0;
        int outside = 0;
        int unresolvedCount = 0;
        Set<String> unresolved = new TreeSet<>();
        try (URLClassLoader libraryLoader = new URLClassLoader(urls(libraries), ClassLoader.getPlatformClassLoader())) {
            MinecraftVersionDiff.Hierarchy hierarchy = new MinecraftVersionDiff.Hierarchy(info, libraryLoader);
            Set<MinecraftVersionDiff.MemberRef> seen = new HashSet<>();
            for (ClassModel model : models) {
                for (PoolEntry entry : model.constantPool()) {
                    if (entry instanceof ClassEntry type) {
                        String name = element(type.asInternalName());
                        if (!classes.containsKey(name)) {
                            checkDefaultPackage(name, identity, defaultPackage);
                        }
                    } else if (entry instanceof MemberRefEntry ref) {
                        String owner = element(ref.owner().asInternalName());
                        if (!classes.containsKey(owner)) {
                            continue;
                        }
                        MinecraftVersionDiff.MemberRef member = new MinecraftVersionDiff.MemberRef(
                                owner, ref.name().stringValue(), ref.type().stringValue(), !(ref instanceof FieldRefEntry));
                        if (!seen.add(member)) {
                            continue;
                        }
                        references++;
                        switch (hierarchy.resolve(member)) {
                            case FOUND, NO_CLASS -> {}
                            case OUTSIDE_JAR -> outside++;
                            case MISSING -> {
                                unresolvedCount++;
                                if (unresolved.size() < EXAMPLES) {
                                    unresolved.add(member.display());
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close the library class loader", e);
        }
        return new Result(
                Set.copyOf(classes.keySet()),
                classes.size(),
                defaultPackage,
                duplicates,
                references,
                List.copyOf(unresolved),
                unresolvedCount,
                outside);
    }

    private static void checkDefaultPackage(String name, Set<String> identity, Set<String> found) {
        if (!name.isEmpty() && !name.contains("/") && !identity.contains(name)) {
            found.add(name);
        }
    }

    /** {@code [Lnet/minecraft/A;} names the class {@code net/minecraft/A}; primitive arrays name none. */
    private static String element(String internalName) {
        if (!internalName.startsWith("[")) {
            return internalName;
        }
        String element = internalName.replaceFirst("^\\[+", "");
        return element.startsWith("L") && element.endsWith(";") ? element.substring(1, element.length() - 1) : "";
    }

    private static URL[] urls(Collection<Path> paths) {
        List<URL> urls = new ArrayList<>();
        for (Path path : paths) {
            try {
                urls.add(path.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Not a valid library path: " + path, e);
            }
        }
        return urls.toArray(URL[]::new);
    }
}
