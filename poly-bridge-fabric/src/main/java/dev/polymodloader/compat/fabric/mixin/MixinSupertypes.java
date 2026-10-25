package dev.polymodloader.compat.fabric.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgedInterfaceInjection;
import dev.polymodloader.compat.bridge.ModJarContents;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * The interfaces a mod's mixins add to their target classes, as Mixin adds them: every interface a mixin class
 * implements, and an interface mixin (an accessor or invoker) itself. Mods call methods of these interfaces on the game
 * type itself (Loom adds them to the game jar the mod is compiled against), so the link check must count them as
 * supertypes of the targets (ADR-0020). They are only read for that; Mixin applies them.
 *
 * <p>Read from the mixin classes the configuration lists for this side ({@code mixins}, plus {@code client} or {@code
 * server}); a configuration plugin may still turn some of them off, so this is a superset.
 */
public final class MixinSupertypes {

    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";

    private MixinSupertypes() {}

    /**
     * The supertypes the configuration at {@code configPath} adds on {@code side}; empty when it cannot be read (the
     * bridge reports that separately).
     */
    public static List<BridgedInterfaceInjection> of(String configPath, ModJarContents jar, GameSide side) {
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
        List<BridgedInterfaceInjection> supertypes = new ArrayList<>();
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
                // A class file ASM cannot read is reported by the link check (POLY-COMPAT-118).
                continue;
            }
            List<String> added = (mixin.access & Opcodes.ACC_INTERFACE) != 0 ? List.of(mixin.name) : mixin.interfaces;
            String source = configPath + ":" + name;
            for (String target : targets(mixin)) {
                for (String interfaceName : added) {
                    supertypes.add(new BridgedInterfaceInjection(source, target, interfaceName, false));
                }
            }
        }
        return supertypes;
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

    /** The internal names {@code @Mixin} names, by class ({@code value}) or by name ({@code targets}). */
    private static List<String> targets(ClassNode mixin) {
        List<String> targets = new ArrayList<>();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (mixin.invisibleAnnotations != null) {
            annotations.addAll(mixin.invisibleAnnotations);
        }
        if (mixin.visibleAnnotations != null) {
            annotations.addAll(mixin.visibleAnnotations);
        }
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals(MIXIN_ANNOTATION) || annotation.values == null) {
                continue;
            }
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                String key = (String) annotation.values.get(i);
                if (!(annotation.values.get(i + 1) instanceof List<?> values)) {
                    continue;
                }
                for (Object value : values) {
                    if (key.equals("value") && value instanceof Type type) {
                        targets.add(type.getInternalName());
                    } else if (key.equals("targets") && value instanceof String name) {
                        targets.add(name.replace('.', '/'));
                    }
                }
            }
        }
        return targets;
    }
}
