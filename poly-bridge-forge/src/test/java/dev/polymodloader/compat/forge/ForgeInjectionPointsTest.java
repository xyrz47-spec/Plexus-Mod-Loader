package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.compat.bridge.InjectionPointReplacement;
import dev.polymodloader.compat.bridge.InjectionPointReplacement.Placement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Where Forge's patch moved the injection points other loaders' Mixins name (ADR-0021 G8). */
class ForgeInjectionPointsTest {

    private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";

    /**
     * Fabric API's {@code PlayerBlockBreakEvents} hook {@code ServerPlayerGameMode.destroyBlock} before {@code
     * Block.playerWillDestroy} (BEFORE, cancellable) and before {@code Block.destroy} (AFTER). Forge's patch calls both
     * inside {@code removeBlock}: BEFORE moves to the first call after the permission checks, before anything changes;
     * AFTER runs after each {@code removeBlock} that removed the block.
     */
    @Test
    void blockBreakingPointsFollowForgesRemoveBlock() {
        List<InjectionPointReplacement> points = ForgeInjectionPoints.all();

        InjectionPointReplacement before = point(points, "Block;playerWillDestroy(");
        assertEquals(GAME_MODE, before.targetClass());
        assertEquals("destroyBlock", before.method());
        assertEquals("Lnet/minecraft/server/level/ServerPlayer;preventsBlockDrops()Z", before.replacement());
        assertEquals(Placement.SAME_PLACE, before.placement());

        InjectionPointReplacement after = point(points, "Block;destroy(");
        assertEquals(GAME_MODE, after.targetClass());
        assertEquals("destroyBlock", after.method());
        assertEquals("L" + GAME_MODE + ";removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", after.replacement());
        assertEquals(Placement.AFTER_TRUE_RESULT, after.placement());
    }

    /**
     * Against Forge's patched client classes ({@code -PforgePatchedClient=<forge-<version>-client.jar>}): each declared
     * method no longer makes the replaced call and makes the replacement call, and each helper of an {@code
     * AFTER_TRUE_RESULT} point makes the replaced call. Run for every pinned Forge release whose client jar is at hand.
     */
    @Test
    @EnabledIfSystemProperty(named = "poly.forgePatchedClient", matches = ".+")
    void everyPointHasItsShapeInForgesPatchedClient() throws IOException {
        try (JarFile jar = new JarFile(
                Path.of(System.getProperty("poly.forgePatchedClient")).toFile())) {
            for (InjectionPointReplacement point : ForgeInjectionPoints.all()) {
                ClassNode target = read(jar, point.targetClass()).orElseThrow();
                MethodNode method = target.methods.stream()
                        .filter(candidate -> candidate.name.equals(point.method()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no " + point.targetClass() + "." + point.method()));
                assertFalse(makes(method, point.replaced()), point + ": the replaced call is still there");
                assertTrue(makes(method, point.replacement()), point + ": the replacement is not there");
                if (point.placement() == Placement.AFTER_TRUE_RESULT) {
                    String helper = point.replacement();
                    String name = helper.substring(helper.indexOf(';') + 1, helper.indexOf('('));
                    String descriptor = helper.substring(helper.indexOf('('));
                    MethodNode helperMethod = target.methods.stream()
                            .filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("no helper " + helper));
                    assertTrue(descriptor.endsWith(")Z"), helper);
                    assertTrue(makes(helperMethod, point.replaced()), point + ": the helper does not make the call");
                    assertEquals(0, helperMethod.access & Opcodes.ACC_STATIC, helper + " is an instance method");
                }
            }
        }
    }

    private static InjectionPointReplacement point(List<InjectionPointReplacement> points, String replaced) {
        return points.stream()
                .filter(point -> point.replaced().contains(replaced))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no point replacing " + replaced));
    }

    /** Whether {@code method} has the call or field access {@code target} (Mixin's notation). */
    private static boolean makes(MethodNode method, String target) {
        String owner = target.substring(1, target.indexOf(';'));
        String rest = target.substring(target.indexOf(';') + 1);
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && (call.name + call.desc).equals(rest)) {
                return true;
            }
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals(owner)
                    && (field.name + ":" + field.desc).equals(rest)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ClassNode> read(JarFile jar, String internalName) throws IOException {
        ZipEntry entry = jar.getEntry(internalName + ".class");
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream in = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, 0);
            return Optional.of(node);
        }
    }
}
