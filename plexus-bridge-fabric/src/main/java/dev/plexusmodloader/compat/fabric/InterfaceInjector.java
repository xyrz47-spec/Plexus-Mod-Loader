package dev.plexusmodloader.compat.fabric;

import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.fabric.loader.FabricFamilyMod;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Adds the interfaces of the loaded Fabric and Quilt mods' {@code inject-interface} lines to game classes, with the meaning of
 * Fabric Loader 0.19.5's class tweaker ({@code InterfaceInjectionClassVisitor}, ADR-0020):
 *
 * <ul>
 *   <li>each interface is appended to the class's interfaces unless the class already has it
 *   <li>when the class has a generic signature, or an injected interface has generic arguments, the signature gets
 *       {@code L<interface>;} for each added interface (built from the superclass and interfaces when there was none)
 *   <li>a nested interface ({@code a/Outer$Api}) gets {@code InnerClasses} entries, as {@code public static abstract
 *       interface}, unless the class already lists it
 * </ul>
 *
 * <p>Fabric Loader also checks that generic arguments only use type variables the class declares; Poly Mod Loader does not
 * repeat that check, so a wrong signature is kept as written (it matters only to reflection).
 *
 * <p>Thread: immutable after construction; {@link #apply} is safe from any thread.
 */
final class InterfaceInjector {

    /** {@code public static abstract interface}, the inner-class flags Fabric Loader writes. */
    private static final int INNER_INTERFACE_ACCESS =
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE;

    private final Map<String, List<ClassTweakerFile.InjectedInterface>> byTarget;

    private InterfaceInjector(Map<String, List<ClassTweakerFile.InjectedInterface>> byTarget) {
        this.byTarget = byTarget;
    }

    /** The injections of {@code lines}, in order. */
    static InterfaceInjector of(List<ClassTweakerFile.InjectedInterface> lines) {
        Map<String, List<ClassTweakerFile.InjectedInterface>> byTarget = new LinkedHashMap<>();
        for (ClassTweakerFile.InjectedInterface line : lines) {
            byTarget.computeIfAbsent(line.target(), target -> new ArrayList<>()).add(line);
        }
        return new InterfaceInjector(byTarget);
    }

    /** The injections of every loaded Fabric and Quilt mod's class tweakers, in load order and then file order. */
    static InterfaceInjector fromMods(BridgeLaunchContext context) {
        return of(classTweakers(context).stream()
                .flatMap(file -> file.injectedInterfaces().stream())
                .toList());
    }

    /**
     * The class tweakers of every loaded Fabric and Quilt mod, in load order and then file order: a Fabric mod's one
     * {@code accessWidener}, each of a Quilt mod's {@code access_widener} entries (ADR-0027).
     */
    static List<ClassTweakerFile> classTweakers(BridgeLaunchContext context) {
        List<ClassTweakerFile> files = new ArrayList<>();
        for (FabricFamilyMod mod : FabricFamilyMod.of(context)) {
            for (String path : mod.classTweakers()) {
                // The bridge assessed this class tweaker already; only a readable, accepted one lets the mod load.
                files.add(ClassTweakerFile.parse(path, read(mod.mod(), path)));
            }
        }
        return files;
    }

    private static String read(BridgeLaunchContext.LoadedMod mod, String path) {
        try (JarFile jar = new JarFile(mod.jar().toFile())) {
            ZipEntry entry = jar.getEntry(path);
            if (entry == null) {
                throw new IllegalStateException(
                        "class tweaker " + path + " of mod " + mod.modId() + " disappeared after it was checked");
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the class tweaker " + path + " of mod " + mod.modId(), e);
        }
    }

    /** Whether no loaded mod injects an interface. */
    boolean isEmpty() {
        return byTarget.isEmpty();
    }

    /** Number of {@code inject-interface} lines. */
    int count() {
        return byTarget.values().stream().mapToInt(List::size).sum();
    }

    /**
     * The class with its injected interfaces.
     *
     * @param className binary or internal name
     * @return {@code bytes} itself when no line targets the class
     */
    byte[] apply(String className, byte[] bytes) {
        List<ClassTweakerFile.InjectedInterface> lines = byTarget.get(className.replace('.', '/'));
        if (lines == null) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new Visitor(writer, lines), 0);
        return writer.toByteArray();
    }

    private static final class Visitor extends ClassVisitor {

        private final List<ClassTweakerFile.InjectedInterface> lines;
        private final Set<String> knownInnerClasses = new HashSet<>();

        Visitor(ClassVisitor next, List<ClassTweakerFile.InjectedInterface> lines) {
            super(Opcodes.ASM9, next);
            this.lines = lines;
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            Set<String> all = new LinkedHashSet<>(Arrays.asList(interfaces == null ? new String[0] : interfaces));
            StringBuilder newSignature = signature == null ? null : new StringBuilder(signature);
            if (newSignature == null && lines.stream().anyMatch(ClassTweakerFile.InjectedInterface::hasGenerics)) {
                newSignature = new StringBuilder("L").append(superName).append(';');
                for (String existing : all) {
                    newSignature.append('L').append(existing).append(';');
                }
            }
            for (ClassTweakerFile.InjectedInterface line : lines) {
                if (all.add(line.interfaceName()) && newSignature != null) {
                    newSignature.append(line.signature());
                }
            }
            super.visit(
                    version,
                    access,
                    name,
                    newSignature == null ? null : newSignature.toString(),
                    superName,
                    all.toArray(String[]::new));
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            knownInnerClasses.add(name);
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public void visitEnd() {
            for (ClassTweakerFile.InjectedInterface line : lines) {
                String interfaceName = line.interfaceName();
                if (!knownInnerClasses.add(interfaceName)) {
                    continue;
                }
                addInnerClassEntries(interfaceName);
            }
            super.visitEnd();
        }

        /** One entry per nesting level of {@code a/Outer$Mid$Api}, as Fabric Loader writes them. */
        private void addInnerClassEntries(String interfaceName) {
            int packageEnd = interfaceName.lastIndexOf('/');
            String simpleName = packageEnd < 0 ? interfaceName : interfaceName.substring(packageEnd + 1);
            String packagePrefix = interfaceName.substring(0, packageEnd + 1);
            int previous = -1;
            int next = -1;
            while ((next = simpleName.indexOf('$', next + 1)) != -1) {
                if (next - previous == 1) {
                    continue;
                }
                if (previous != -1) {
                    String outer = packagePrefix + simpleName.substring(0, previous);
                    String inner = simpleName.substring(previous + 1, next);
                    super.visitInnerClass(outer + "$" + inner, outer, inner, INNER_INTERFACE_ACCESS);
                }
                previous = next;
            }
            if (previous != -1 && previous != simpleName.length()) {
                String outer = packagePrefix + simpleName.substring(0, previous);
                String inner = simpleName.substring(previous + 1);
                super.visitInnerClass(outer + "$" + inner, outer, inner, INNER_INTERFACE_ACCESS);
            }
        }
    }
}
