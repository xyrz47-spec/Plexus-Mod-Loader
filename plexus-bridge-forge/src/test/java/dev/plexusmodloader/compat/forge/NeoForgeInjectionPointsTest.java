package dev.plexusmodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.plexusmodloader.compat.bridge.InjectionPointReplacement;
import dev.plexusmodloader.compat.bridge.InjectionPointReplacement.Placement;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * {@link NeoForgeInjectionPoints} against the classes they describe (ADR-0026, ADR-0030). Run with the client jar
 * NeoForge's installer patched ({@code -PneoForgePatchedClient=<minecraft-client-patched-<version>.jar>}, in a NeoForge
 * client run's {@code .plexus/neoforge/<version>/libraries/}) and the vanilla client jar ({@code
 * -PneoForgeVanillaJar=<client-<mc>.jar>}) of every pinned NeoForge release at hand. Points are written in Mixin's
 * target notation; {@code (P...)LOwner;} is a constructor of {@code Owner}, as HostPatchAdjustments reads it.
 */
class NeoForgeInjectionPointsTest {

    /**
     * Points of one NeoForge release only (target class, method, replaced call): NeoForge 26.2.0.93's bed calls, which
     * 26.3.0.98 no longer patches (and 26.3's vanilla stopSleeping calls setBlockAndUpdate). In a jar of another
     * release, vanilla's code is still there; the tests check these only where the release has them.
     */
    private static final List<String> RELEASE_POINTS = List.of(
            "net/minecraft/world/entity/LivingEntity getBedOrientation",
            "net/minecraft/world/entity/LivingEntity lambda$stopSleeping$0");

    private static boolean releasePoint(InjectionPointReplacement point) {
        return RELEASE_POINTS.contains(point.targetClass() + " " + point.method());
    }

    /** Each replaced call is one vanilla makes in the method; a point that names a call vanilla lacks moves nothing. */
    @Test
    @EnabledIfSystemProperty(named = "plexus.neoForgeVanillaJar", matches = ".+")
    void everyReplacedCallIsInVanilla() throws IOException {
        List<String> problems = new ArrayList<>();
        try (JarFile jar = new JarFile(
                Path.of(System.getProperty("plexus.neoForgeVanillaJar")).toFile())) {
            for (InjectionPointReplacement point : NeoForgeInjectionPoints.all()) {
                Optional<ClassModel> target = read(jar, point.targetClass());
                if (target.isEmpty()) {
                    problems.add(point + ": no class " + point.targetClass());
                } else if (named(target.get(), point.method()).stream().noneMatch(m -> makes(m, point.replaced()))
                        && !releasePoint(point)) {
                    problems.add(point + ": vanilla " + point.method() + " does not make " + point.replaced());
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    /**
     * In NeoForge's patched client: for {@code SAME_PLACE} and {@code AFTER_TRUE_RESULT}, no method of that name makes
     * the replaced call any more and one makes the replacement (and the helper of an {@code AFTER_TRUE_RESULT} point
     * makes the replaced call); for {@code IN_METHOD}, the replacement method makes the replaced call and no other method
     * of that name does.
     */
    @Test
    @EnabledIfSystemProperty(named = "plexus.neoForgePatchedClient", matches = ".+")
    void everyPointHasItsShapeInNeoForgesPatchedClient() throws IOException {
        List<String> problems = new ArrayList<>();
        try (JarFile jar = new JarFile(
                Path.of(System.getProperty("plexus.neoForgePatchedClient")).toFile())) {
            for (InjectionPointReplacement point : NeoForgeInjectionPoints.all()) {
                Optional<ClassModel> target = read(jar, point.targetClass());
                if (target.isEmpty()) {
                    problems.add(point + ": no class " + point.targetClass());
                    continue;
                }
                List<MethodModel> methods = named(target.get(), point.method());
                if (point.placement() == Placement.IN_METHOD) {
                    Optional<MethodModel> moved = method(target.get(), point.replacement());
                    if (moved.isEmpty()) {
                        problems.add(point + ": no method " + point.replacement());
                    } else if (!makes(moved.get(), point.replaced())) {
                        problems.add(point + ": " + point.replacement() + " does not make the replaced call");
                    }
                    if (methods.stream()
                            .filter(m -> moved.isEmpty() || m != moved.get())
                            .anyMatch(m -> makes(m, point.replaced()))) {
                        problems.add(point + ": " + point.method() + " still makes the replaced call");
                    }
                    continue;
                }
                if (releasePoint(point) && methods.stream().noneMatch(m -> makes(m, point.replacement()))) {
                    continue;
                }
                if (methods.stream().anyMatch(m -> makes(m, point.replaced()))) {
                    problems.add(point + ": the replaced call is still there");
                }
                if (methods.stream().noneMatch(m -> makes(m, point.replacement()))) {
                    problems.add(point + ": the replacement is not there");
                }
                if (point.placement() == Placement.AFTER_TRUE_RESULT) {
                    String helper = point.replacement();
                    Optional<MethodModel> helperMethod =
                            method(target.get(), helper.substring(helper.indexOf(';') + 1));
                    if (helperMethod.isEmpty() || !makes(helperMethod.get(), point.replaced())) {
                        problems.add(point + ": the helper does not make the replaced call");
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    private static List<MethodModel> named(ClassModel model, String name) {
        return model.methods().stream()
                .filter(method -> method.methodName().equalsString(name))
                .toList();
    }

    /**
     * The method {@code nameAndDescriptor} ({@code name(descriptor)}) of {@code model}, or the first one named {@code
     * nameAndDescriptor} when it has no descriptor (as Mixin selects a method by name).
     */
    private static Optional<MethodModel> method(ClassModel model, String nameAndDescriptor) {
        int open = nameAndDescriptor.indexOf('(');
        String name = open < 0 ? nameAndDescriptor : nameAndDescriptor.substring(0, open);
        String descriptor = open < 0 ? null : nameAndDescriptor.substring(open);
        return model.methods().stream()
                .filter(method -> method.methodName().equalsString(name)
                        && (descriptor == null || method.methodType().equalsString(descriptor)))
                .findFirst();
    }

    /** Whether {@code method} has the call, field access or constructor call {@code target} names. */
    private static boolean makes(MethodModel method, String target) {
        if (method.code().isEmpty()) {
            return false;
        }
        for (CodeElement element : method.code().get()) {
            if (matches(element, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(CodeElement element, String target) {
        if (target.startsWith("(")) {
            String owner = target.substring(target.lastIndexOf(')') + 2, target.length() - 1);
            String constructor = target.substring(0, target.lastIndexOf(')') + 1) + "V";
            return element instanceof InvokeInstruction call
                    && call.opcode() == Opcode.INVOKESPECIAL
                    && call.name().equalsString("<init>")
                    && call.owner().asInternalName().equals(owner)
                    && call.type().equalsString(constructor);
        }
        String owner = target.substring(1, target.indexOf(';'));
        String rest = target.substring(target.indexOf(';') + 1);
        if (element instanceof InvokeInstruction call) {
            return call.owner().asInternalName().equals(owner)
                    && (call.name().stringValue() + call.type().stringValue()).equals(rest);
        }
        if (element instanceof FieldInstruction field) {
            return field.owner().asInternalName().equals(owner)
                    && (field.name().stringValue() + ":" + field.type().stringValue()).equals(rest);
        }
        return false;
    }

    private static Optional<ClassModel> read(JarFile jar, String internalName) throws IOException {
        ZipEntry entry = jar.getEntry(internalName + ".class");
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream in = jar.getInputStream(entry)) {
            return Optional.of(ClassFile.of().parse(in.readAllBytes()));
        }
    }
}
