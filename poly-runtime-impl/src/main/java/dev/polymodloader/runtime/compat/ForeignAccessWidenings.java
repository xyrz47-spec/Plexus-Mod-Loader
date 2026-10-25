package dev.polymodloader.runtime.compat;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgedAccessWidening;
import dev.polymodloader.internal.transform.AccessWidening;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.runtime.discovery.ForeignModResolver;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the access widenings that loadable foreign mods ask for (Fabric class tweakers, ADR-0018) into
 * transform-engine widenings, after {@link ModLinkVerifier} has checked their targets. The planner then
 * validates and joins them with Poly Mod Loader's and Poly Mods' own, exactly like {@code accessWidenings} in
 * {@code poly.mod.json} (ADR-0013).
 *
 * <ul>
 *   <li>A widening applies to the running side and this game version; the class tweaker names no version, and
 *       the link check has just verified the target in this game's jar.
 *   <li>A target this game does not have (class or exact member) is left out, as Fabric Loader applies such a
 *       line to nothing; the link check reported it ({@code POLY-COMPAT-137}). Client classes on a dedicated server
 *       are left out the same way ({@code POLY-COMPAT-130}).
 *   <li>Its failure policy is to stop the launch: a target the planner cannot find after this filtering would
 *       mean the two disagree, and the mod's code would fail later with {@link IllegalAccessError}.
 *   <li>Making a record class accessible also makes its canonical constructor accessible, as Fabric Loader's
 *       access widener does.
 * </ul>
 */
public final class ForeignAccessWidenings {

    private ForeignAccessWidenings() {}

    /**
     * The engine widenings of {@code mods}.
     *
     * @param mods the foreign mods that will load
     * @param gameVersion the running Minecraft version
     * @param side the running side
     * @param gameClass reads a class file of the game jar by internal name; empty when the jar has none
     */
    public static List<AccessWidening> from(
            List<ForeignModResolver.ForeignMod> mods,
            String gameVersion,
            GameSide side,
            Function<String, Optional<byte[]>> gameClass) {
        List<AccessWidening> widenings = new ArrayList<>();
        for (ForeignModResolver.ForeignMod mod : mods) {
            String modId = mod.assessment().modId();
            for (BridgedAccessWidening widening : mod.assessment().accessWidenings()) {
                Optional<byte[]> classFile = gameClass.apply(widening.owner());
                if (classFile.isEmpty() || !declares(classFile.get(), widening)) {
                    continue;
                }
                widenings.add(engineWidening(modId, gameVersion, side, widening));
                if (widening.kind() == BridgedAccessWidening.Kind.CLASS
                        && widening.access().contains(BridgedAccessWidening.Access.ACCESSIBLE)) {
                    classFile
                            .flatMap(ForeignAccessWidenings::canonicalConstructor)
                            .ifPresent(descriptor -> widenings.add(new AccessWidening(
                                    modId,
                                    gameVersion,
                                    Set.of(side),
                                    widening.owner(),
                                    AccessWidening.Kind.METHOD,
                                    "<init>",
                                    descriptor,
                                    Set.of(AccessWidening.Access.ACCESSIBLE),
                                    TransformFailurePolicy.ABORT_STARTUP)));
                }
            }
        }
        return widenings;
    }

    private static AccessWidening engineWidening(
            String modId, String gameVersion, GameSide side, BridgedAccessWidening widening) {
        Set<AccessWidening.Access> access = EnumSet.noneOf(AccessWidening.Access.class);
        for (BridgedAccessWidening.Access requested : widening.access()) {
            access.add(AccessWidening.Access.valueOf(requested.name()));
        }
        return new AccessWidening(
                modId,
                gameVersion,
                Set.of(side),
                widening.owner(),
                AccessWidening.Kind.valueOf(widening.kind().name()),
                widening.name(),
                widening.descriptor(),
                access,
                TransformFailurePolicy.ABORT_STARTUP);
    }

    /** Whether the class declares the widened member; always for a class widening. */
    private static boolean declares(byte[] classFile, BridgedAccessWidening widening) {
        if (widening.kind() == BridgedAccessWidening.Kind.CLASS) {
            return true;
        }
        ClassModel model;
        try {
            model = ClassFile.of().parse(classFile);
        } catch (IllegalArgumentException e) {
            // The planner reads the same bytes and reports a class it cannot parse.
            return true;
        }
        if (widening.kind() == BridgedAccessWidening.Kind.FIELD) {
            return model.fields().stream()
                    .anyMatch(field -> field.fieldName().equalsString(widening.name())
                            && field.fieldType().equalsString(widening.descriptor()));
        }
        return model.methods().stream()
                .anyMatch(method -> method.methodName().equalsString(widening.name())
                        && method.methodType().equalsString(widening.descriptor()));
    }

    /** The descriptor of a record's canonical constructor, when the class is a record that declares one. */
    private static Optional<String> canonicalConstructor(byte[] classFile) {
        ClassModel model;
        try {
            model = ClassFile.of().parse(classFile);
        } catch (IllegalArgumentException e) {
            // The planner reads the same bytes and reports a class it cannot parse.
            return Optional.empty();
        }
        Optional<RecordAttribute> record = model.findAttribute(Attributes.record());
        if (record.isEmpty()) {
            return Optional.empty();
        }
        String descriptor = record.get().components().stream()
                .map(RecordComponentInfo::descriptor)
                .map(java.lang.classfile.constantpool.Utf8Entry::stringValue)
                .collect(Collectors.joining("", "(", ")V"));
        boolean declared = model.methods().stream()
                .anyMatch(method -> method.methodName().equalsString("<init>")
                        && method.methodType().equalsString(descriptor));
        return declared ? Optional.of(descriptor) : Optional.empty();
    }
}
