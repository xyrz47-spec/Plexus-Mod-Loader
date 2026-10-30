package dev.plexusmodloader.devtools.gradle.minecraft;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinecraftVersionDiffTest {

    private static final ClassDesc A = ClassDesc.of("net.minecraft.A");
    private static final ClassDesc B = ClassDesc.of("net.minecraft.B");

    @TempDir
    Path directory;

    @Test
    void reportsMissingClassesMembersAndChangedClassesTheModulesUse() throws IOException {
        Path fromJar = jar("from.jar", Map.of(
                "net/minecraft/A", classA(true),
                "net/minecraft/B", classB(),
                "net/minecraft/Gone", emptyClass("net.minecraft.Gone"),
                "net/minecraft/Unused", emptyClass("net.minecraft.Unused")));
        Path toJar = jar("to.jar", Map.of(
                "net/minecraft/A", classA(false),
                "net/minecraft/B", classB(),
                "net/minecraft/Added", emptyClass("net.minecraft.Added")));
        Path modules = directory.resolve("classes");
        Files.createDirectories(modules.resolve("dev/example"));
        Files.write(modules.resolve("dev/example/Layer.class"), moduleClass());

        MinecraftVersionDiff.Report report = MinecraftVersionDiff.compare(
                "26.2", versionJson(25, "3.4.1", true), fromJar, "26.3", versionJson(26, "3.4.3", false), toJar, null,
                List.of(modules));

        assertEquals(Set.of("net/minecraft/A", "net/minecraft/B", "net/minecraft/Gone"), report.usedClasses());
        assertEquals(Set.of("net/minecraft/Gone"), report.missingClasses());
        assertEquals(Set.of("net/minecraft/A"), report.changedClasses());
        // B.tick() was inherited from A; Object.toString() resolves outside the jar in both versions.
        assertEquals(1, report.memberChanges().size(), report.memberChanges().toString());
        MinecraftVersionDiff.MemberChange change = report.memberChanges().getFirst();
        assertEquals("net/minecraft/B", change.member().owner());
        assertEquals("tick", change.member().name());
        assertEquals(MinecraftVersionDiff.Resolution.FOUND, change.from());
        assertEquals(MinecraftVersionDiff.Resolution.MISSING, change.to());
        assertEquals(1, report.addedClassCount());
        assertEquals(2, report.removedClassCount());
        assertEquals(1, report.changedClassCount());
        assertFalse(report.hotfixCandidate());

        assertEquals(
                List.of(
                        new MinecraftVersionDiff.LibraryChange("org.lwjgl:lwjgl", "3.4.1", "3.4.3"),
                        new MinecraftVersionDiff.LibraryChange("org.lwjgl:lwjgl-glfw", "3.4.1", null),
                        new MinecraftVersionDiff.LibraryChange("org.lwjgl:lwjgl-sdl", null, "3.4.3")),
                report.libraryChanges());
        assertEquals(List.of("--sun-misc-unsafe-memory-access=allow"), report.removedJvmArguments());
        assertEquals(List.of("-XX:StackShadowPages=32"), report.addedJvmArguments());

        String markdown = MinecraftVersionDiff.markdown(report);
        assertTrue(markdown.contains("needs its own modules (newMinecraftVersion -Pfrom=26.2 -Pto=26.3 -Pjava=26)"), markdown);
        assertTrue(markdown.contains("`net.minecraft.B.tick ()V`: not declared by the class or its supertypes"), markdown);
        assertTrue(markdown.contains("`org.lwjgl:lwjgl-glfw`: 3.4.1 → removed"), markdown);
    }

    @Test
    void sameJarsAreAHotfixCandidate() throws IOException {
        Path jar = jar("same.jar", Map.of("net/minecraft/A", classA(true), "net/minecraft/B", classB()));
        Path modules = directory.resolve("classes");
        Files.createDirectories(modules.resolve("dev/example"));
        Files.write(modules.resolve("dev/example/Layer.class"), moduleClass());
        JsonObject json = versionJson(25, "3.4.1", true);

        MinecraftVersionDiff.Report report =
                MinecraftVersionDiff.compare("26.2", json, jar, "26.2.1", json, jar, null, List.of(modules));

        assertTrue(report.hotfixCandidate(), report.toString());
        assertTrue(MinecraftVersionDiff.markdown(report).contains("26.2.1 may run on the 26.2 modules as a hotfix"));
    }

    /** An obfuscated jar (a = A, d = B) is read through the mappings, so the same Mojang names resolve. */
    @Test
    void readsAnObfuscatedJarThroughMojangMappings() throws IOException {
        Path fromJar = jar("from.jar", Map.of(
                "net/minecraft/A", classA(true),
                "net/minecraft/B", classB(),
                "net/minecraft/Gone", emptyClass("net.minecraft.Gone")));
        ClassDesc obfuscatedA = ClassDesc.of("a");
        byte[] a = ClassFile.of().build(obfuscatedA, builder -> {
            builder.withSuperclass(CD_Object);
            builder.withField("c", CD_int, ClassFile.ACC_PUBLIC);
            builder.withMethod(
                    "b", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, method -> method.withCode(code -> code.return_()));
        });
        byte[] d = ClassFile.of().build(ClassDesc.of("d"), builder -> builder.withSuperclass(obfuscatedA));
        Path toJar = jar("obfuscated.jar", Map.of("a", a, "d", d));
        MojangMappings mappings = MojangMappings.parse(List.of(
                "# {\"id\":\"com.android.tools.r8.mapping\",\"version\":\"2.2\"}",
                "net.minecraft.A -> a:",
                "    int count -> c",
                "    1:1:void tick() -> b",
                "net.minecraft.B -> d:"));
        Path modules = directory.resolve("classes");
        Files.createDirectories(modules.resolve("dev/example"));
        Files.write(modules.resolve("dev/example/Layer.class"), moduleClass());

        MinecraftVersionDiff.Report report = MinecraftVersionDiff.compare(
                "26.1", versionJson(25, "3.4.1", true), fromJar, "1.21.11", versionJson(21, "3.3.3", true), toJar,
                mappings, List.of(modules));

        assertTrue(report.toObfuscated());
        assertEquals(Set.of("net/minecraft/Gone"), report.missingClasses());
        assertEquals(List.of(), report.memberChanges(), report.memberChanges().toString());
        assertEquals(Set.of(), report.changedClasses());
        assertEquals(-1, report.changedClassCount());
        assertFalse(report.hotfixCandidate());
        String markdown = MinecraftVersionDiff.markdown(report);
        assertTrue(markdown.contains("needs a remapped game jar and its own modules"), markdown);
        assertTrue(markdown.contains("changes not compared"), markdown);
        assertFalse(markdown.contains("## Used classes that changed"), markdown);
    }

    @Test
    void mappingsTranslateClassesMembersAndDescriptors() {
        MojangMappings mappings = MojangMappings.parse(List.of(
                "net.minecraft.world.level.Level -> dfx:",
                "    java.lang.String name -> a",
                "    12:14:net.minecraft.world.level.Level[] neighbours(int,net.minecraft.world.level.Level) -> b",
                "    15:15:void net.minecraft.Other.inlined():30:30 -> c",
                "    16:16:boolean isClientSide():40:40 -> c",
                "net.minecraft.Other -> eaa:"));

        assertEquals("net/minecraft/world/level/Level", mappings.className("dfx"));
        assertEquals("java/lang/String", mappings.className("java/lang/String"));
        assertEquals("name", mappings.fieldName("dfx", "a"));
        assertEquals("neighbours", mappings.methodName("dfx", "b", "(ILdfx;)[Ldfx;"));
        assertEquals("isClientSide", mappings.methodName("dfx", "c", "()Z"));
        // The inlined frame names no method of this class; the obfuscated name stays.
        assertEquals("c", mappings.methodName("dfx", "c", "()V"));
        assertEquals("(Lnet/minecraft/Other;)[Lnet/minecraft/world/level/Level;", mappings.descriptor("(Leaa;)[Ldfx;"));
        assertEquals(2, mappings.classCount());
    }

    private static byte[] classA(boolean withTick) {
        return ClassFile.of().build(A, builder -> {
            builder.withSuperclass(CD_Object);
            builder.withField("count", CD_int, ClassFile.ACC_PUBLIC);
            if (withTick) {
                builder.withMethod(
                        "tick", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, method -> method.withCode(code -> code.return_()));
            }
        });
    }

    private static byte[] classB() {
        return ClassFile.of().build(B, builder -> builder.withSuperclass(A));
    }

    private static byte[] emptyClass(String name) {
        return ClassFile.of().build(ClassDesc.of(name), builder -> builder.withSuperclass(CD_Object));
    }

    /** Calls B.tick() and B.toString(), reads A.count, and names net.minecraft.Gone as text. */
    private static byte[] moduleClass() {
        return ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                .build(ClassDesc.of("dev.example.Layer"), builder -> builder.withSuperclass(CD_Object)
                        .withMethod(
                                "use",
                                MethodTypeDesc.of(CD_void, B),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                method -> method.withCode(code -> code.aload(0)
                                        .invokevirtual(B, "tick", MethodTypeDesc.of(CD_void))
                                        .aload(0)
                                        .getfield(A, "count", CD_int)
                                        .pop()
                                        .aload(0)
                                        .invokevirtual(B, "toString", MethodTypeDesc.of(CD_String))
                                        .pop()
                                        .ldc("net.minecraft.Gone")
                                        .pop()
                                        .return_())));
    }

    private Path jar(String name, Map<String, byte[]> classes) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(classes).entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    private static JsonObject versionJson(int java, String lwjgl, boolean glfw) {
        String window = glfw ? "lwjgl-glfw" : "lwjgl-sdl";
        String removedArgument = glfw ? "\"--sun-misc-unsafe-memory-access=allow\"," : "\"-XX:StackShadowPages=32\",";
        return JsonParser.parseString("""
                        {
                          "javaVersion": {"component": "java-runtime-epsilon", "majorVersion": %d},
                          "arguments": {"jvm": [
                            {"rules": [{"action": "allow", "os": {"name": "osx"}}], "value": ["-XstartOnFirstThread"]},
                            %s
                            "-cp", "${classpath}"
                          ]},
                          "libraries": [
                            {"name": "org.lwjgl:lwjgl:%s"},
                            {"name": "org.lwjgl:lwjgl:%s:natives-windows"},
                            {"name": "org.lwjgl:%s:%s"},
                            {"name": "org.lwjgl:%s:%s:natives-linux"}
                          ]
                        }
                        """
                        .formatted(java, removedArgument, lwjgl, lwjgl, window, lwjgl, window, lwjgl))
                .getAsJsonObject();
    }
}
