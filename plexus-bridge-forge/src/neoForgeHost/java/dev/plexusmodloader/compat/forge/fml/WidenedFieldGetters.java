package dev.plexusmodloader.compat.forge.fml;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldModel;
import java.lang.reflect.AccessFlag;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoForge's coremod {@code ReplaceFieldWithGetterAccess} redirects reads of some private instance fields of the game to
 * getters (its biome modifiers: {@code Biome.climateSettings}) and refuses a field that is not private: {@code
 * IllegalStateException("Field <name> in <class> is not private and an instance field")}. Another loader's mod may
 * have widened that field (Fabric API's biome class tweaker, ADR-0026 30), as under Forge's {@code
 * FieldToMethodTransformer} (ADR-0021 G8). When FML's processors refuse a class that way, they run once more on the
 * class with its widened instance fields private again, as in the game NeoForge patched, and each such field then gets
 * back the widest access of the widening and of FML's own processors (an access transformer may widen it too).
 *
 * <p>Thread: any; no state.
 */
final class WidenedFieldGetters {

    private static final Pattern REFUSAL =
            Pattern.compile("Field (\\S+) in (\\S+) is not private and an instance field");

    private WidenedFieldGetters() {}

    /**
     * {@code bytes} after {@code processors}; when they refuse a widened field of {@code bytes} in the way above, after
     * {@code processors} on the class with its widened instance fields private.
     */
    static byte[] process(byte[] bytes, UnaryOperator<byte[]> processors) {
        try {
            return processors.apply(bytes);
        } catch (IllegalStateException e) {
            Matcher refusal = REFUSAL.matcher(String.valueOf(e.getMessage()));
            if (!refusal.find()) {
                throw e;
            }
            Map<String, Integer> widened = new HashMap<>();
            byte[] narrowed = narrowed(bytes, widened);
            if (!widened.containsKey(refusal.group(1))) {
                throw e;
            }
            return restored(processors.apply(narrowed), widened);
        }
    }

    /** {@code bytes} with every non-private instance field private; {@code widened} gets their flags. */
    private static byte[] narrowed(byte[] bytes, Map<String, Integer> widened) {
        ClassModel model = ClassFile.of().parse(bytes);
        ClassTransform transform = (builder, element) -> {
            if (element instanceof FieldModel field && widened(field)) {
                int flags = field.flags().flagsMask();
                widened.put(field.fieldName().stringValue(), flags);
                builder.withField(field.fieldName(), field.fieldType(), fieldBuilder -> {
                    field.forEach(fieldElement -> {
                        if (!(fieldElement instanceof java.lang.classfile.AccessFlags)) {
                            fieldBuilder.with(fieldElement);
                        }
                    });
                    fieldBuilder.withFlags(
                            (flags & ~(ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) | ClassFile.ACC_PRIVATE);
                });
            } else {
                builder.with(element);
            }
        };
        return ClassFile.of().transformClass(model, transform);
    }

    private static boolean widened(FieldModel field) {
        return !field.flags().has(AccessFlag.PRIVATE) && !field.flags().has(AccessFlag.STATIC);
    }

    /** {@code bytes} with each field of {@code widened} as visible as before or after, and final only if both were. */
    private static byte[] restored(byte[] bytes, Map<String, Integer> widened) {
        ClassModel model = ClassFile.of().parse(bytes);
        ClassTransform transform = (builder, element) -> {
            Optional<Integer> before = element instanceof FieldModel field
                    ? Optional.ofNullable(widened.get(field.fieldName().stringValue()))
                    : Optional.empty();
            if (before.isEmpty()) {
                builder.with(element);
                return;
            }
            FieldModel field = (FieldModel) element;
            int after = field.flags().flagsMask();
            int visibility = wider(before.get(), after);
            int flags =
                    (after & ~(ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_PRIVATE)) | visibility;
            if ((before.get() & ClassFile.ACC_FINAL) == 0) {
                flags &= ~ClassFile.ACC_FINAL;
            }
            int merged = flags;
            builder.withField(field.fieldName(), field.fieldType(), fieldBuilder -> {
                field.forEach(fieldElement -> {
                    if (!(fieldElement instanceof java.lang.classfile.AccessFlags)) {
                        fieldBuilder.with(fieldElement);
                    }
                });
                fieldBuilder.withFlags(merged);
            });
        };
        return ClassFile.of().transformClass(model, transform);
    }

    private static int wider(int first, int second) {
        return rank(first) >= rank(second) ? visibility(first) : visibility(second);
    }

    private static int visibility(int flags) {
        return flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_PRIVATE);
    }

    private static int rank(int flags) {
        if ((flags & ClassFile.ACC_PUBLIC) != 0) {
            return 3;
        }
        if ((flags & ClassFile.ACC_PROTECTED) != 0) {
            return 2;
        }
        return (flags & ClassFile.ACC_PRIVATE) != 0 ? 0 : 1;
    }
}
