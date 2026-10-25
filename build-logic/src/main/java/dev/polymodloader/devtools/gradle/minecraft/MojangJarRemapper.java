package dev.polymodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.reflect.AccessFlag;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Remaps an obfuscated Minecraft jar (before 26.1) to Mojang names with Mojang's official mappings
 * (ADR-0019 point 4): every class, field and method the mappings list gets its Mojang name, in declarations
 * and in every reference, descriptor, signature and lambda. A reference through a subclass that does not
 * declare the member is looked up in the class's supertypes in the jar, the way the JVM resolves it.
 *
 * <p>The output is deterministic: entries are sorted and carry a fixed time, so the same jar and mappings
 * give the same bytes. Signature files are removed, since the classes no longer match them; the manifest
 * keeps its main attributes, and resources such as {@code version.json} are copied as they are.
 *
 * <p>Runs on the developer's machine only. The remapped jar is Minecraft code and stays in the local cache;
 * it is never committed or published (ADR-0019 point 3).
 */
final class MojangJarRemapper {

    /**
     * Bumped whenever the output for the same input changes, so cached jars made by an older remapper are
     * made again.
     */
    static final int REVISION = 2;

    private static final Pattern SIGNATURE_FILE = Pattern.compile("META-INF/[^/]+\\.(SF|RSA|DSA|EC)|META-INF/SIG-[^/]+");
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

    /**
     * What the remap did.
     *
     * @param classCount classes in the jar
     * @param renamedClassCount classes the mappings list, now under their Mojang names
     * @param unlistedClasses classes in the jar the mappings do not list (they keep their names)
     * @param absentListedClassCount classes the mappings list that the jar does not have (removed by the
     *     obfuscator, such as {@code package-info})
     * @param removedSignatureFiles the signature files left out
     * @param otherMetaInfFiles files under {@code META-INF/} that were copied as they are and may name classes
     * @param unknownTypeMerges times two types met in a frame and one of them was in neither the jar, the
     *     libraries nor the JDK, so {@code java/lang/Object} was used (the load check verifies the result)
     */
    record Result(
            int classCount,
            int renamedClassCount,
            List<String> unlistedClasses,
            int absentListedClassCount,
            List<String> removedSignatureFiles,
            List<String> otherMetaInfFiles,
            int unknownTypeMerges) {}

    private MojangJarRemapper() {}

    /** Remaps {@code input} into {@code output} without libraries for frame computation (tests). */
    static Result remap(Path input, MojangMappings mappings, Path output) {
        return remap(input, mappings, List.of(), output);
    }

    /**
     * Remaps {@code input} into {@code output}, replacing it if it exists.
     *
     * <p>Stack map frames are computed again. The obfuscator keeps only live locals in a frame, so at the last
     * {@code return} of a method a frame may hold no locals at all, and a hook that a transformation rule
     * inserts there could not load {@code this} or a parameter (ADR-0007 rules assume javac's frames, which
     * keep them). Recomputed frames hold every local assigned on the way, like javac's. Merging two types needs
     * their superclasses: the jar's classes, then {@code libraries} and the JDK.
     */
    static Result remap(Path input, MojangMappings mappings, List<Path> libraries, Path output) {
        Map<String, byte[]> classes = new TreeMap<>(MinecraftVersionDiff.readClasses(input));
        Map<String, Declarations> declarations = new HashMap<>();
        classes.forEach((name, bytes) -> declarations.put(name, Declarations.of(ClassFile.of().parse(bytes))));
        HierarchyRemapper remapper = new HierarchyRemapper(mappings, declarations);

        Map<String, byte[]> entries = new TreeMap<>();
        List<String> unlisted = new ArrayList<>();
        int renamed = 0;
        int unknownMerges;
        try (URLClassLoader outside = new URLClassLoader(urls(libraries), ClassLoader.getPlatformClassLoader())) {
            TypeHierarchy hierarchy = new TypeHierarchy(remapper, declarations, outside);
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                if (mappings.listsClass(entry.getKey())) {
                    renamed++;
                } else {
                    unlisted.add(entry.getKey());
                }
                ClassReader reader = new ClassReader(entry.getValue());
                ClassWriter writer = new FrameComputingWriter(hierarchy);
                reader.accept(new ClassRemapper(writer, remapper), ClassReader.SKIP_FRAMES);
                String name = remapper.map(entry.getKey()) + ".class";
                if (entries.put(name, writer.toByteArray()) != null) {
                    throw new IllegalStateException("Two classes of " + input + " remap to " + name
                            + "; the mappings do not match this jar.");
                }
            }
            unknownMerges = hierarchy.unknownMerges;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close the library class loader", e);
        }
        int absent = (int) mappings.obfuscatedClasses().stream()
                .filter(name -> !classes.containsKey(name))
                .count();

        List<String> signatures = new ArrayList<>();
        List<String> otherMetaInf = new ArrayList<>();
        Manifest manifest = new Manifest();
        try (JarFile jar = new JarFile(input.toFile())) {
            Manifest original = jar.getManifest();
            if (original != null) {
                manifest.getMainAttributes().putAll(original.getMainAttributes());
            }
            for (JarEntry entry : jar.stream().toList()) {
                String name = entry.getName();
                if (entry.isDirectory() || isClass(name) || name.equals(JarFile.MANIFEST_NAME)) {
                    continue;
                }
                if (SIGNATURE_FILE.matcher(name).matches()) {
                    signatures.add(name);
                    continue;
                }
                if (name.startsWith("META-INF/")) {
                    otherMetaInf.add(name);
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    entries.put(name, in.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + input, e);
        }
        if (manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION) == null) {
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }
        write(output, manifest, entries);
        return new Result(
                classes.size(),
                renamed,
                List.copyOf(unlisted),
                absent,
                List.copyOf(new TreeSet<>(signatures)),
                List.copyOf(new TreeSet<>(otherMetaInf)),
                unknownMerges);
    }

    private static URL[] urls(List<Path> paths) {
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

    /** A {@link ClassWriter} that computes frames with {@link TypeHierarchy} instead of loading classes. */
    private static final class FrameComputingWriter extends ClassWriter {

        private final TypeHierarchy hierarchy;

        FrameComputingWriter(TypeHierarchy hierarchy) {
            super(ClassWriter.COMPUTE_FRAMES);
            this.hierarchy = hierarchy;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return hierarchy.commonSuperClass(type1, type2);
        }
    }

    /**
     * Superclasses by Mojang name, for merging two types in a frame: the jar's classes (renamed), then the
     * libraries and the JDK through {@code outside}.
     */
    private static final class TypeHierarchy {

        private record Type(String superName, boolean isInterface) {}

        private final Map<String, Type> game = new HashMap<>();
        private final ClassLoader outside;
        private final Map<String, Type> outsideTypes = new HashMap<>();
        private final Set<String> missing = new HashSet<>();
        private int unknownMerges;

        TypeHierarchy(HierarchyRemapper remapper, Map<String, Declarations> declarations, ClassLoader outside) {
            this.outside = outside;
            declarations.forEach((name, type) -> game.put(
                    remapper.map(name),
                    new Type(type.superName() == null ? null : remapper.map(type.superName()), type.isInterface())));
        }

        String commonSuperClass(String type1, String type2) {
            if (type1.equals(type2)) {
                return type1;
            }
            Type first = type(type1);
            Type second = type(type2);
            if (first == null || second == null) {
                unknownMerges++;
                return "java/lang/Object";
            }
            if (first.isInterface() || second.isInterface()) {
                return "java/lang/Object";
            }
            Set<String> ancestors = new HashSet<>();
            for (String current = type1; current != null; ) {
                ancestors.add(current);
                Type type = type(current);
                if (type == null) {
                    unknownMerges++;
                    return "java/lang/Object";
                }
                current = type.superName();
            }
            for (String current = type2; current != null; ) {
                if (ancestors.contains(current)) {
                    return current;
                }
                Type type = type(current);
                if (type == null) {
                    unknownMerges++;
                    return "java/lang/Object";
                }
                current = type.superName();
            }
            return "java/lang/Object";
        }

        private Type type(String name) {
            Type known = game.get(name);
            if (known != null || missing.contains(name)) {
                return known;
            }
            Type cached = outsideTypes.get(name);
            if (cached != null) {
                return cached;
            }
            try (InputStream in = outside.getResourceAsStream(name + ".class")) {
                if (in == null) {
                    missing.add(name);
                    return null;
                }
                ClassModel model = ClassFile.of().parse(in.readAllBytes());
                Type type = new Type(
                        model.superclass().map(entry -> entry.asInternalName()).orElse(null),
                        model.flags().has(AccessFlag.INTERFACE));
                outsideTypes.put(name, type);
                return type;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read the class " + name, e);
            }
        }
    }

    private static boolean isClass(String name) {
        return name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class");
    }

    private static void write(Path output, Manifest manifest, Map<String, byte[]> entries) {
        try {
            Files.createDirectories(output.getParent());
            Path partial = output.resolveSibling(output.getFileName() + ".partial");
            try (OutputStream file = Files.newOutputStream(partial);
                    JarOutputStream jar = new JarOutputStream(file)) {
                JarEntry manifestEntry = new JarEntry(JarFile.MANIFEST_NAME);
                manifestEntry.setTimeLocal(ENTRY_TIME);
                jar.putNextEntry(manifestEntry);
                manifest.write(jar);
                jar.closeEntry();
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    JarEntry jarEntry = new JarEntry(entry.getKey());
                    jarEntry.setTimeLocal(ENTRY_TIME);
                    jar.putNextEntry(jarEntry);
                    jar.write(entry.getValue());
                    jar.closeEntry();
                }
            }
            Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + output, e);
        }
    }

    /**
     * What one obfuscated class declares, for resolving references through its supertypes.
     *
     * @param superName the superclass, or null for {@code java/lang/Object}
     * @param methods {@code name + descriptor} of each declared method, with whether it is inherited
     *     (neither private nor, in an interface, static)
     * @param fields {@code name + descriptor} of each declared field, with whether it is inherited (not private)
     */
    private record Declarations(
            String superName,
            List<String> interfaces,
            boolean isInterface,
            Map<String, Boolean> methods,
            Map<String, Boolean> fields) {

        static Declarations of(ClassModel model) {
            boolean isInterface = model.flags().has(AccessFlag.INTERFACE);
            Map<String, Boolean> methods = new HashMap<>();
            model.methods().forEach(method -> methods.put(
                    method.methodName().stringValue() + method.methodType().stringValue(),
                    !method.flags().has(AccessFlag.PRIVATE)
                            && !(isInterface && method.flags().has(AccessFlag.STATIC))));
            Map<String, Boolean> fields = new HashMap<>();
            model.fields().forEach(field -> fields.put(
                    field.fieldName().stringValue() + field.fieldType().stringValue(),
                    !field.flags().has(AccessFlag.PRIVATE)));
            return new Declarations(
                    model.superclass().map(entry -> entry.asInternalName()).orElse(null),
                    model.interfaces().stream().map(entry -> entry.asInternalName()).toList(),
                    isInterface,
                    methods,
                    fields);
        }
    }

    /**
     * Names from the mappings. A member is named by the class that declares it: the owner of the reference
     * if it declares the member, otherwise the first supertype in the jar that does and passes it on.
     * Supertypes outside the jar (JDK, libraries) are not followed: no game class extends past them, and the
     * names they declare are not obfuscated.
     */
    private static final class HierarchyRemapper extends Remapper {

        private final MojangMappings mappings;
        private final Map<String, Declarations> declarations;

        HierarchyRemapper(MojangMappings mappings, Map<String, Declarations> declarations) {
            super(Opcodes.ASM9);
            this.mappings = mappings;
            this.declarations = declarations;
        }

        @Override
        public String map(String internalName) {
            return mappings.listsClass(internalName) ? mappings.className(internalName) : internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (name.startsWith("<") || owner.startsWith("[")) {
                return name;
            }
            String declaring = methodOwner(owner, name + descriptor);
            if (declaring == null) {
                return name;
            }
            String mapped = mappings.listedMethod(declaring, name, descriptor);
            return mapped == null ? name : mapped;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String declaring = fieldOwner(owner, name + descriptor, new HashSet<>(), true);
            if (declaring == null) {
                return name;
            }
            String mapped = mappings.listedField(declaring, name);
            return mapped == null ? name : mapped;
        }

        @Override
        public String mapRecordComponentName(String owner, String name, String descriptor) {
            return mapFieldName(owner, name, descriptor);
        }

        @Override
        public String mapAnnotationAttributeName(String descriptor, String name) {
            if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
                return name;
            }
            String annotation = descriptor.substring(1, descriptor.length() - 1);
            Declarations type = declarations.get(annotation);
            if (type == null) {
                return name;
            }
            for (String method : type.methods().keySet()) {
                if (method.startsWith(name + "()")) {
                    String mapped = mappings.listedMethod(annotation, name, method.substring(name.length()));
                    return mapped == null ? name : mapped;
                }
            }
            return name;
        }

        /** The class in the jar that declares the method, searching superclasses first, then interfaces. */
        private String methodOwner(String owner, String key) {
            Declarations own = declarations.get(owner);
            if (own == null) {
                return null;
            }
            if (own.methods().containsKey(key)) {
                return owner;
            }
            Deque<String> interfaces = new ArrayDeque<>(own.interfaces());
            for (String current = own.superName(); current != null; ) {
                Declarations type = declarations.get(current);
                if (type == null) {
                    break;
                }
                if (Boolean.TRUE.equals(type.methods().get(key))) {
                    return current;
                }
                interfaces.addAll(type.interfaces());
                current = type.superName();
            }
            Set<String> seen = new HashSet<>();
            while (!interfaces.isEmpty()) {
                String current = interfaces.removeFirst();
                Declarations type = declarations.get(current);
                if (type == null || !seen.add(current)) {
                    continue;
                }
                if (Boolean.TRUE.equals(type.methods().get(key))) {
                    return current;
                }
                interfaces.addAll(type.interfaces());
            }
            return null;
        }

        /** The class in the jar that declares the field: the class, then its interfaces, then its superclass. */
        private String fieldOwner(String owner, String key, Set<String> seen, boolean isOwner) {
            Declarations type = declarations.get(owner);
            if (type == null || !seen.add(owner)) {
                return null;
            }
            Boolean inherited = type.fields().get(key);
            if (inherited != null && (isOwner || inherited)) {
                return owner;
            }
            for (String parent : type.interfaces()) {
                String found = fieldOwner(parent, key, seen, false);
                if (found != null) {
                    return found;
                }
            }
            return type.superName() == null ? null : fieldOwner(type.superName(), key, seen, false);
        }
    }
}
