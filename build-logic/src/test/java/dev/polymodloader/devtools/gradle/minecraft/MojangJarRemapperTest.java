package dev.polymodloader.devtools.gradle.minecraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The remapper and its self-check on a small "obfuscated" jar compiled by javac: classes in the default
 * package with one-letter names, an inherited field and methods reached through a subclass, a lambda of a
 * game interface, and an inner class.
 */
class MojangJarRemapperTest {

    private static final Map<String, String> SOURCES = Map.of(
            "a.java", "public class a { public int a; public void a() {} public static int b(int x) { return x; } }",
            "b.java", "public class b extends a { public void c() { a(); this.a = b(1); } }",
            "c.java", "public interface c { void a(String s); }",
            "d.java", "public class d { public c a() { return s -> new b().c(); } public static class a {} }");

    private static final List<String> MAPPINGS = List.of(
            "# a comment line",
            "net.minecraft.Base -> a:",
            "    int count -> a",
            "    1:1:void tick() -> a",
            "    2:2:int scale(int) -> b",
            "net.minecraft.Child -> b:",
            "    3:3:void run() -> c",
            "net.minecraft.Action -> c:",
            "    void accept(java.lang.String) -> a",
            "net.minecraft.User -> d:",
            "    4:4:net.minecraft.Action action() -> a",
            "net.minecraft.User$Inner -> d$a:",
            "net.minecraft.package-info -> e:");

    @TempDir
    Path directory;

    @Test
    void remapsDeclarationsInheritedReferencesAndLambdas() throws IOException {
        Path input = obfuscatedJar("input.jar", compile("all", SOURCES));
        MojangMappings mappings = MojangMappings.parse(MAPPINGS);
        Path output = directory.resolve("mojang.jar");

        MojangJarRemapper.Result result = MojangJarRemapper.remap(input, mappings, output);

        assertEquals(5, result.classCount());
        assertEquals(5, result.renamedClassCount());
        assertEquals(List.of(), result.unlistedClasses());
        assertEquals(1, result.absentListedClassCount(), "package-info is listed but not in the jar");
        assertEquals(List.of("META-INF/TEST.SF"), result.removedSignatureFiles());
        Map<String, byte[]> classes = MinecraftVersionDiff.readClasses(output);
        assertEquals(
                Set.of(
                        "net/minecraft/Base",
                        "net/minecraft/Child",
                        "net/minecraft/Action",
                        "net/minecraft/User",
                        "net/minecraft/User$Inner"),
                classes.keySet());

        ClassModel base = ClassFile.of().parse(classes.get("net/minecraft/Base"));
        assertEquals(
                Set.of("<init>()V", "tick()V", "scale(I)I"),
                Set.copyOf(base.methods().stream()
                        .map(method -> method.methodName().stringValue() + method.methodType().stringValue())
                        .toList()));
        assertEquals("count", base.fields().getFirst().fieldName().stringValue());

        // b.c() called a() and b(1) and set a through b, which declares none of them.
        ClassModel child = ClassFile.of().parse(classes.get("net/minecraft/Child"));
        assertEquals("net/minecraft/Base", child.superclass().orElseThrow().asInternalName());
        List<String> references = memberReferences(child);
        assertTrue(references.contains("net/minecraft/Child.tick()V"), references.toString());
        assertTrue(references.contains("net/minecraft/Child.scale(I)I"), references.toString());
        assertTrue(references.contains("net/minecraft/Child.count:I"), references.toString());
        assertTrue(child.methods().stream().anyMatch(method -> method.methodName().equalsString("run")));

        // The lambda's interface method is named through the interface.
        ClassModel user = ClassFile.of().parse(classes.get("net/minecraft/User"));
        List<String> lambdas = entries(user)
                .filter(InvokeDynamicEntry.class::isInstance)
                .map(entry -> ((InvokeDynamicEntry) entry).name().stringValue()
                        + ((InvokeDynamicEntry) entry).type().stringValue())
                .toList();
        assertEquals(List.of("accept()Lnet/minecraft/Action;"), lambdas);
        assertTrue(user.methods().stream().anyMatch(method -> method.methodName().equalsString("action")));

        try (var jar = new java.util.jar.JarFile(output.toFile())) {
            assertTrue(jar.getEntry("version.json") != null, "resources are kept");
            assertEquals("dev.example.Main", jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertTrue(jar.getManifest().getEntries().isEmpty(), "per-entry digests are dropped");
        }

        RemappedJarCheck.Result check = RemappedJarCheck.check(output, mappings, List.of());
        assertTrue(check.passed(), check.toString());
        assertTrue(check.referenceCount() > 0);
        RemappedJarLoadCheck.Result load = RemappedJarLoadCheck.check(output, List.of());
        assertEquals(List.of(), load.failures());
        assertEquals(5, load.loaded());
    }

    @Test
    void sameInputGivesTheSameBytes() throws IOException {
        Path input = obfuscatedJar("input.jar", compile("all", SOURCES));
        MojangMappings mappings = MojangMappings.parse(MAPPINGS);
        Path first = directory.resolve("first.jar");
        Path second = directory.resolve("second.jar");

        MojangJarRemapper.remap(input, mappings, first);
        MojangJarRemapper.remap(input, mappings, second);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void selfCheckFindsUnmappedClassesAndReferencesNothingDeclares() throws IOException {
        // b calls a.a(), but the jar's a no longer has it; c is missing from the mappings.
        Map<String, byte[]> classes = new TreeMap<>(compile("all", SOURCES));
        classes.putAll(compile("changed", Map.of("a.java", "public class a { public int a; public static int b(int x) {"
                + " return x; } }")));
        Path input = obfuscatedJar("broken.jar", classes);
        List<String> mappings = new ArrayList<>(MAPPINGS);
        mappings.removeIf(line -> line.startsWith("net.minecraft.Action") || line.contains("accept"));
        MojangMappings parsed = MojangMappings.parse(mappings);
        Path output = directory.resolve("broken-mojang.jar");

        MojangJarRemapper.Result result = MojangJarRemapper.remap(input, parsed, output);
        RemappedJarCheck.Result check = RemappedJarCheck.check(output, parsed, List.of());

        assertEquals(List.of("c"), result.unlistedClasses());
        assertFalse(check.passed());
        assertEquals(Set.of("c"), check.defaultPackageClasses());
        assertEquals(1, check.unresolvedReferenceCount(), check.toString());
        assertEquals(List.of("net.minecraft.Child.a ()V"), check.unresolvedReferences());
    }

    @Test
    void framesAreComputedAgainSoAReturnFrameKeepsThisAndTheParameters() throws IOException {
        // Like the obfuscator's output: the frame at the last return keeps no locals, as none is read after it.
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V1_8, org.objectweb.asm.Opcodes.ACC_PUBLIC, "f", null,
                "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor method =
                writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "a", "(I)V", null, null);
        method.visitCode();
        org.objectweb.asm.Label end = new org.objectweb.asm.Label();
        method.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1);
        method.visitJumpInsn(org.objectweb.asm.Opcodes.IFEQ, end);
        method.visitLabel(end);
        method.visitFrame(org.objectweb.asm.Opcodes.F_FULL, 0, null, 0, null);
        method.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        method.visitMaxs(1, 2);
        method.visitEnd();
        writer.visitEnd();
        Path input = obfuscatedJar("frames.jar", Map.of("f", writer.toByteArray()));
        MojangMappings mappings = MojangMappings.parse(List.of("net.minecraft.Frames -> f:", "    1:1:void run(int) -> a"));
        Path output = directory.resolve("frames-mojang.jar");

        MojangJarRemapper.Result result = MojangJarRemapper.remap(input, mappings, output);

        assertEquals(0, result.unknownTypeMerges());
        org.objectweb.asm.tree.ClassNode remapped = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(MinecraftVersionDiff.readClasses(output).get("net/minecraft/Frames"))
                .accept(remapped, 0);
        org.objectweb.asm.tree.MethodNode run = remapped.methods.stream()
                .filter(candidate -> candidate.name.equals("run"))
                .findFirst()
                .orElseThrow();
        List<org.objectweb.asm.tree.FrameNode> frames = new ArrayList<>();
        run.instructions.forEach(instruction -> {
            if (instruction instanceof org.objectweb.asm.tree.FrameNode frame) {
                frames.add(frame);
            }
        });
        assertEquals(1, frames.size());
        org.objectweb.asm.tree.FrameNode frame = frames.getFirst();
        // ASM writes it compressed as "same as the method start": this and the int parameter.
        assertTrue(
                frame.type == org.objectweb.asm.Opcodes.F_SAME
                        || (frame.local != null && frame.local.size() == 2),
                "frame type " + frame.type + " locals " + frame.local);
        RemappedJarLoadCheck.Result load = RemappedJarLoadCheck.check(output, List.of());
        assertEquals(List.of(), load.failures());
    }

    @Test
    void libraryClassesOfAnotherOsAreNotRemapProblems() {
        MojangMappings mappings = MojangMappings.parse(MAPPINGS);
        Set<String> jar = Set.of("net/minecraft/Base");

        assertTrue(RemappedJarCheck.isLibraryClass("ca/weblite/objc/NSObject", jar, mappings));
        assertFalse(RemappedJarCheck.isLibraryClass("net/minecraft/Base", jar, mappings));
        assertFalse(RemappedJarCheck.isLibraryClass("net/minecraft/Child", jar, mappings), "a Mojang name");
        assertFalse(RemappedJarCheck.isLibraryClass("zz", jar, mappings), "the default package");
    }

    private static Stream<PoolEntry> entries(ClassModel model) {
        return StreamSupport.stream(model.constantPool().spliterator(), false);
    }

    private static List<String> memberReferences(ClassModel model) {
        return entries(model)
                .filter(MemberRefEntry.class::isInstance)
                .map(entry -> {
                    MemberRefEntry ref = (MemberRefEntry) entry;
                    boolean field = ref instanceof java.lang.classfile.constantpool.FieldRefEntry;
                    return ref.owner().asInternalName() + "." + ref.name().stringValue() + (field ? ":" : "")
                            + ref.type().stringValue();
                })
                .toList();
    }

    /** Compiles default-package sources and returns their classes by internal name. */
    private Map<String, byte[]> compile(String name, Map<String, String> sources) throws IOException {
        Path sourceDir = directory.resolve(name + "-src");
        Path classDir = directory.resolve(name + "-classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classDir);
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-d", classDir.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceDir.resolve(source.getKey());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)), "javac failed");
        Map<String, byte[]> classes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(classDir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String relative = classDir.relativize(file).toString().replace('\\', '/');
                classes.put(relative.substring(0, relative.length() - ".class".length()), Files.readAllBytes(file));
            }
        }
        return classes;
    }

    /** A jar like Mojang's: a manifest with a main class and per-entry digests, a signature file, a resource. */
    private Path obfuscatedJar(String name, Map<String, byte[]> classes) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "dev.example.Main");
        Attributes digest = new Attributes();
        digest.putValue("SHA-256-Digest", "AAAA");
        manifest.getEntries().put("a.class", digest);
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file, manifest)) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                out.write(entry.getValue());
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("META-INF/TEST.SF"));
            out.write("Signature-Version: 1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("version.json"));
            out.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}
