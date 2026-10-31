package dev.plexusmodloader.compat.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.compat.bridge.ModJarContents;
import dev.plexusmodloader.compat.fabric.mixin.MixinConfigEntry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * An injection point whose {@code target} Mixin's own selector parser refuses (Even Better Moon and Sun 1.0.0 and
 * 1.1.0: {@code "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose:()V"}, a colon before the descriptor) fails when its
 * target class loads, which for a required configuration stops that class from loading. Under Fabric Loader and Quilt
 * Loader, which use the same Mixin, the client then never finishes loading resources. Plexus Mod Loader decides it
 * before the game starts.
 */
class MixinTargetCheckTest {

    private static final String BAD = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose:()V";
    private static final String GOOD = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V";

    @Test
    void aRequiredConfigurationWithATargetMixinRefusesLeavesTheModOut() {
        List<CompatibilityFinding> findings = check(true, BAD, "INVOKE", GameSide.CLIENT);

        CompatibilityFinding finding = find(findings, "PLEXUS-COMPAT-140");
        assertTrue(finding.blocking());
        assertTrue(finding.detail().contains("SkyMixin"), finding.detail());
        assertTrue(finding.detail().contains(BAD), finding.detail());
        assertTrue(finding.detail().contains("pushPose:"), finding.detail());
    }

    @Test
    void anOptionalConfigurationOnlyLosesThatMixin() {
        CompatibilityFinding finding = find(check(false, BAD, "INVOKE", GameSide.CLIENT), "PLEXUS-COMPAT-140");

        assertFalse(finding.blocking());
    }

    @Test
    void validTargetsAndOtherSidesAndPointsWithoutTargetsPass() {
        assertNoFinding(check(true, GOOD, "INVOKE", GameSide.CLIENT));
        assertNoFinding(check(true, "Lnet/minecraft/A;field:I", "FIELD", GameSide.CLIENT));
        // The mixin is listed under "client": on a dedicated server Mixin never reads it.
        assertNoFinding(check(true, BAD, "INVOKE", GameSide.DEDICATED_SERVER));
        // HEAD reads no target.
        assertNoFinding(check(true, BAD, "HEAD", GameSide.CLIENT));
    }

    private static void assertNoFinding(List<CompatibilityFinding> findings) {
        assertEquals(
                List.of(),
                findings.stream()
                        .filter(finding -> finding.code().equals("PLEXUS-COMPAT-140"))
                        .toList());
    }

    private static CompatibilityFinding find(List<CompatibilityFinding> findings, String code) {
        return findings.stream()
                .filter(finding -> finding.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " not in " + findings));
    }

    private static List<CompatibilityFinding> check(boolean required, String target, String at, GameSide side) {
        Map<String, byte[]> files = new HashMap<>();
        files.put(
                "sky.mixins.json",
                ("{\"required\": " + required + ", \"package\": \"com.example.sky\", \"client\": [\"SkyMixin\"]}")
                        .getBytes(StandardCharsets.UTF_8));
        files.put("com/example/sky/SkyMixin.class", mixinClass(target, at));
        ModJarContents jar = path -> Optional.ofNullable(files.get(path));
        List<CompatibilityFinding> findings = new ArrayList<>();
        MixinConfigCheck.check(
                List.of(new MixinConfigEntry("sky.mixins.json", "*")),
                "fabric.mod.json",
                "Fabric Loader",
                side,
                jar,
                findings);
        return findings;
    }

    /** {@code @Mixin(SkyRenderer.class) class SkyMixin { @Inject(method = "render", at = @At(value, target)) ... }}. */
    private static byte[] mixinClass(String target, String at) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/sky/SkyMixin", null, "java/lang/Object", null);
        AnnotationVisitor mixin = writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        AnnotationVisitor targets = mixin.visitArray("value");
        targets.visit(null, Type.getObjectType("net/minecraft/client/renderer/SkyRenderer"));
        targets.visitEnd();
        mixin.visitEnd();
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PRIVATE,
                "onRender",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                null,
                null);
        AnnotationVisitor inject = method.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
        AnnotationVisitor methods = inject.visitArray("method");
        methods.visit(null, "render");
        methods.visitEnd();
        AnnotationVisitor ats = inject.visitArray("at");
        AnnotationVisitor point = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
        point.visit("value", at);
        point.visit("target", target);
        point.visitEnd();
        ats.visitEnd();
        inject.visitEnd();
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
