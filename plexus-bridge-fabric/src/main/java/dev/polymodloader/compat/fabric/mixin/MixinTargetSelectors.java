package dev.polymodloader.compat.fabric.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.ModJarContents;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.selectors.InvalidSelectorException;
import org.spongepowered.asm.mixin.injection.selectors.TargetSelector;

/**
 * Injection points whose {@code target} Mixin's own selector parser refuses ({@link TargetSelector#parseAndValidate}),
 * the call Mixin makes when it creates the injector, as its target class loads. Mixin then fails that class (for a
 * required configuration) or skips the mixin. Fabric Loader and Quilt Loader use the same Mixin, so the outcome is
 * theirs too; Poly Mod Loader reads it from the jar before the game starts (Quilt full support Q6: Even Better Moon and
 * Sun's {@code "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose:()V"}).
 *
 * <p>Only the points that read a member target are checked ({@code INVOKE}, {@code INVOKE_ASSIGN}, {@code
 * INVOKE_STRING}, {@code FIELD}), in the mixin classes the configuration lists for this side.
 */
public final class MixinTargetSelectors {

    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final Set<String> MEMBER_POINTS = Set.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD");

    private MixinTargetSelectors() {}

    /** Each refused target of the configuration at {@code configPath} on {@code side}, as a readable line. */
    public static List<String> refused(String configPath, ModJarContents jar, GameSide side) {
        Optional<JsonObject> config = MixinConfigFiles.read(configPath, jar);
        if (config.isEmpty()
                || !(config.get().get("package") instanceof JsonElement packageElement)
                || !packageElement.isJsonPrimitive()) {
            return List.of();
        }
        String mixinPackage = packageElement.getAsString();
        List<String> names = new ArrayList<>();
        names(config.get().get("mixins"), names);
        names(config.get().get(side == GameSide.CLIENT ? "client" : "server"), names);
        List<String> refused = new ArrayList<>();
        for (String name : names) {
            String className = (mixinPackage.isEmpty() ? name : mixinPackage + "." + name).replace('.', '/');
            Optional<byte[]> bytes;
            try {
                bytes = jar.read(className + ".class");
            } catch (UncheckedIOException e) {
                continue;
            }
            if (bytes.isEmpty()) {
                continue;
            }
            ClassNode mixin = new ClassNode();
            try {
                new ClassReader(bytes.get()).accept(mixin, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            } catch (RuntimeException e) {
                // A class file ASM cannot read is reported by the link check (PLEXUS-COMPAT-118).
                continue;
            }
            for (MethodNode method : mixin.methods) {
                for (List<AnnotationNode> annotations :
                        List.of(nonNull(method.visibleAnnotations), nonNull(method.invisibleAnnotations))) {
                    for (AnnotationNode annotation : annotations) {
                        visit(annotation, name + "." + method.name, refused);
                    }
                }
            }
        }
        return refused;
    }

    /**
     * Whether the configuration is {@code required}: Mixin stops on a failing mixin of such a configuration and only
     * logs one of another.
     */
    public static boolean required(String configPath, ModJarContents jar) {
        return MixinConfigFiles.read(configPath, jar)
                .map(config -> config.get("required") instanceof JsonElement value
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isBoolean()
                        && value.getAsBoolean())
                .orElse(false);
    }

    private static List<AnnotationNode> nonNull(List<AnnotationNode> annotations) {
        return annotations == null ? List.of() : annotations;
    }

    /** Checks every {@code @At} inside {@code annotation}, however deep ({@code at}, {@code slice}, arrays). */
    private static void visit(AnnotationNode annotation, String where, List<String> refused) {
        if (annotation.desc.equals(AT)) {
            check(annotation, where, refused);
        }
        if (annotation.values == null) {
            return;
        }
        for (int i = 1; i < annotation.values.size(); i += 2) {
            visitValue(annotation.values.get(i), where, refused);
        }
    }

    private static void visitValue(Object value, String where, List<String> refused) {
        if (value instanceof AnnotationNode nested) {
            visit(nested, where, refused);
        } else if (value instanceof List<?> list) {
            list.forEach(element -> visitValue(element, where, refused));
        }
    }

    private static void check(AnnotationNode at, String where, List<String> refused) {
        String point = null;
        String target = null;
        for (int i = 0; i + 1 < at.values.size(); i += 2) {
            if (at.values.get(i).equals("value") && at.values.get(i + 1) instanceof String text) {
                point = text;
            } else if (at.values.get(i).equals("target") && at.values.get(i + 1) instanceof String text) {
                target = text;
            }
        }
        if (point == null || target == null || target.isEmpty() || !MEMBER_POINTS.contains(point)) {
            return;
        }
        try {
            TargetSelector.parseAndValidate(target, null);
        } catch (InvalidSelectorException e) {
            refused.add(where + " @At(\"" + point + "\") target \"" + target + "\": " + e.getMessage());
        } catch (RuntimeException e) {
            // A selector Mixin can only judge with its mixin context (remapping, dynamic selectors) is left to Mixin.
        }
    }

    private static void names(JsonElement list, List<String> names) {
        if (list instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    names.add(element.getAsString());
                }
            }
        }
    }
}
