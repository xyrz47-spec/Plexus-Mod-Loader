package dev.polymodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The game jar as Fabric's build tools give it to a mod's compiler: with the class tweakers of {@link #getClassTweakers}
 * applied ({@code accessible}, {@code extendable}, {@code mutable}, {@code inject-interface}, each maybe {@code
 * transitive-}), so that code written against widened members and injected interfaces compiles (Poly Mod Loader's
 * NeoForge host modules, ADR-0031). Only for compiling: at run time Poly Mod Loader applies the same class tweakers to
 * the running game. Classes are changed in their access flags and interface lists only; other entries are copied.
 */
@CacheableTask
public abstract class ClassTweakedJarTask extends DefaultTask {

    /** The jar to change, such as NeoForge's patched client jar. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    /** Class tweaker files ({@code classTweaker v1} or {@code accessWidener v2} headers). */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getClassTweakers();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    /** What the class tweakers ask for one class. */
    static final class Changes {
        boolean accessible;
        boolean extendable;
        final Set<String> accessibleMembers = new HashSet<>();
        final Set<String> extendableMembers = new HashSet<>();
        final Set<String> mutableFields = new HashSet<>();
        final Set<String> interfaces = new LinkedHashSet<>();
    }

    @TaskAction
    public void apply() throws IOException {
        Map<String, Changes> changes = new HashMap<>();
        for (var file : getClassTweakers().getFiles()) {
            read(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8), changes, file.getName());
        }
        // Classes made accessible must be public in every InnerClasses attribute too: javac reads access from there.
        Set<String> accessibleClasses = new HashSet<>();
        changes.forEach((name, change) -> {
            if (change.accessible || change.extendable) {
                accessibleClasses.add(name);
            }
        });
        Path output = getOutputJar().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        try (JarFile jar = new JarFile(getInputJar().get().getAsFile());
                OutputStream file = Files.newOutputStream(output);
                JarOutputStream out = new JarOutputStream(file)) {
            for (JarEntry entry : java.util.Collections.list(jar.entries())) {
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/") && isSignature(entry.getName())) {
                    continue;
                }
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                if (entry.getName().endsWith(".class")) {
                    String name = entry.getName().substring(0, entry.getName().length() - 6);
                    bytes = transform(bytes, changes.get(name), accessibleClasses);
                }
                out.putNextEntry(new JarEntry(entry.getName()));
                out.write(bytes);
                out.closeEntry();
            }
        }
    }

    private static boolean isSignature(String name) {
        return name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC");
    }

    /** Adds what {@code lines} of class tweaker {@code source} ask to {@code changes}. */
    static void read(List<String> lines, Map<String, Changes> changes, String source) {
        boolean header = true;
        for (String raw : lines) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (header) {
                header = false;
                if (!(parts[0].equals("classTweaker") || parts[0].equals("accessWidener"))) {
                    throw new GradleException(source + " is not a class tweaker: " + raw);
                }
                continue;
            }
            String kind = parts[0].startsWith("transitive-") ? parts[0].substring("transitive-".length()) : parts[0];
            if (kind.equals("inject-interface")) {
                changes.computeIfAbsent(parts[1], key -> new Changes()).interfaces.add(parts[2]);
                continue;
            }
            if (parts.length < 3) {
                throw new GradleException(source + ": cannot read " + raw);
            }
            Changes change = changes.computeIfAbsent(parts[2], key -> new Changes());
            String member = parts.length >= 5 ? parts[3] + parts[4] : null;
            switch (kind + " " + parts[1]) {
                case "accessible class" -> change.accessible = true;
                case "extendable class" -> change.extendable = true;
                case "accessible method", "accessible field" -> change.accessibleMembers.add(member);
                case "extendable method" -> change.extendableMembers.add(member);
                case "mutable field" -> change.mutableFields.add(member);
                default -> throw new GradleException(source + ": unknown entry " + raw);
            }
        }
    }

    private static int opened(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
    }

    static byte[] transform(byte[] bytes, Changes change, Set<String> accessibleClasses) {
        ClassReader reader = new ClassReader(bytes);
        if (change == null && accessibleClasses.isEmpty()) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        reader.accept(
                new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public void visit(
                            int version,
                            int access,
                            String name,
                            String signature,
                            String superName,
                            String[] interfaces) {
                        int flags = access;
                        String[] all = interfaces;
                        if (change != null) {
                            if (change.accessible || change.extendable) {
                                flags = opened(flags);
                            }
                            if (change.extendable) {
                                flags &= ~Opcodes.ACC_FINAL;
                            }
                            if (!change.interfaces.isEmpty()) {
                                List<String> list = new ArrayList<>(Arrays.asList(interfaces));
                                StringBuilder generic = signature == null ? null : new StringBuilder(signature);
                                for (String face : change.interfaces) {
                                    if (!list.contains(face)) {
                                        list.add(face);
                                        // A generic signature lists the interfaces too, after the superclass.
                                        if (generic != null) {
                                            generic.append('L').append(face).append(';');
                                        }
                                    }
                                }
                                all = list.toArray(String[]::new);
                                signature = generic == null ? null : generic.toString();
                            }
                        }
                        super.visit(version, flags, name, signature, superName, all);
                    }

                    @Override
                    public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        super.visitInnerClass(
                                name, outerName, innerName, accessibleClasses.contains(name) ? opened(access) : access);
                    }

                    @Override
                    public FieldVisitor visitField(
                            int access, String name, String descriptor, String signature, Object value) {
                        int flags = access;
                        if (change != null && change.accessibleMembers.contains(name + descriptor)) {
                            flags = opened(flags);
                        }
                        if (change != null && change.mutableFields.contains(name + descriptor)) {
                            flags &= ~Opcodes.ACC_FINAL;
                        }
                        return super.visitField(flags, name, descriptor, signature, value);
                    }

                    @Override
                    public MethodVisitor visitMethod(
                            int access, String name, String descriptor, String signature, String[] exceptions) {
                        int flags = access;
                        if (change != null && change.accessibleMembers.contains(name + descriptor)) {
                            flags = opened(flags);
                        }
                        if (change != null && change.extendableMembers.contains(name + descriptor)) {
                            flags = opened(flags) & ~Opcodes.ACC_FINAL;
                        }
                        return super.visitMethod(flags, name, descriptor, signature, exceptions);
                    }
                },
                0);
        return writer.toByteArray();
    }
}
