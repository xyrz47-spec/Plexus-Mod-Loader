package dev.plexusmodloader.internal.transform;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Widens access to one class, method or field of the game, with the meaning of Fabric's access
 * wideners so a bridge can reuse it:
 *
 * <ul>
 *   <li>{@link Access#ACCESSIBLE}: the class or member becomes {@code public}. A private method also
 *       becomes {@code final}, so making it public cannot let a subclass change what the class's own
 *       calls do.
 *   <li>{@link Access#EXTENDABLE}: a class becomes {@code public}, non-{@code final} and non-sealed; a
 *       method becomes non-{@code final} and at least {@code protected}, and the owner's own {@code
 *       invokespecial} calls to it become virtual calls so overrides take effect. A constructor becomes at
 *       least {@code protected}, so subclasses can call it; calls to it stay {@code invokespecial}.
 *   <li>{@link Access#MUTABLE}: a field becomes non-{@code final}. A {@code static final} field with a
 *       constant value is copied into the code that reads it when that code is compiled, so changing
 *       it at run time does not reach such readers. Fields of an interface stay {@code final}, which the
 *       JVM requires; Fabric's access widener leaves them alone too.
 * </ul>
 *
 * <p>Widenings only add access, so any number of them on one member combine (their access sets are
 * joined) without an order. The planner checks that the class and the exact member exist.
 *
 * @param ownerModId mod (or {@code poly}) that asks for the widening
 * @param gameVersion exact Minecraft version the target was verified against
 * @param sides processes it applies to
 * @param ownerInternalName the class, or the class declaring the member, as a JVM internal name
 * @param kind what is widened
 * @param name the member name; empty for {@link Kind#CLASS}
 * @param descriptor the member's JVM descriptor; empty for {@link Kind#CLASS}
 * @param access what to allow; not empty
 * @param failurePolicy what to do when the target does not exist
 */
@ApiStability(StabilityLevel.INTERNAL)
public record AccessWidening(
        String ownerModId,
        String gameVersion,
        Set<GameSide> sides,
        String ownerInternalName,
        Kind kind,
        String name,
        String descriptor,
        Set<Access> access,
        TransformFailurePolicy failurePolicy) {

    private static final Pattern INTERNAL_NAME = Pattern.compile("[^./;\\[]+(/[^./;\\[]+)*");
    private static final Pattern METHOD_DESCRIPTOR = Pattern.compile("\\(.*\\).+");
    private static final Pattern FIELD_DESCRIPTOR = Pattern.compile("\\[*([BCDFIJSZ]|L[^;]+;)");

    /** What is widened. */
    public enum Kind {
        CLASS,
        METHOD,
        FIELD
    }

    /** What a widening allows. */
    public enum Access {
        ACCESSIBLE,
        EXTENDABLE,
        MUTABLE
    }

    public AccessWidening {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(gameVersion, "gameVersion");
        sides = Set.copyOf(sides);
        if (sides.isEmpty()) {
            throw new IllegalArgumentException("an access widening must apply to at least one side");
        }
        if (!INTERNAL_NAME.matcher(ownerInternalName).matches()) {
            throw new IllegalArgumentException(
                    "Owner must be a JVM internal name with '/' separators: " + ownerInternalName);
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        access = access.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(access));
        if (access.isEmpty()) {
            throw new IllegalArgumentException("an access widening must allow something");
        }
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        switch (kind) {
            case CLASS -> {
                if (!name.isEmpty() || !descriptor.isEmpty()) {
                    throw new IllegalArgumentException("a class widening has no member name or descriptor");
                }
                requireOnly(access, EnumSet.of(Access.ACCESSIBLE, Access.EXTENDABLE), "a class");
            }
            case METHOD -> {
                requireMemberName(name);
                if (!METHOD_DESCRIPTOR.matcher(descriptor).matches()) {
                    throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
                }
                requireOnly(access, EnumSet.of(Access.ACCESSIBLE, Access.EXTENDABLE), "a method");
                if (name.equals("<clinit>")) {
                    throw new IllegalArgumentException("a class initialiser cannot be widened");
                }
            }
            case FIELD -> {
                requireMemberName(name);
                if (!FIELD_DESCRIPTOR.matcher(descriptor).matches()) {
                    throw new IllegalArgumentException("Invalid field descriptor: " + descriptor);
                }
                requireOnly(access, EnumSet.of(Access.ACCESSIBLE, Access.MUTABLE), "a field");
            }
        }
    }

    /** A readable name of the target, for example {@code net/minecraft/A.field:I}. */
    public String describeTarget() {
        return switch (kind) {
            case CLASS -> "class " + ownerInternalName;
            case METHOD -> "method " + ownerInternalName + "." + name + descriptor;
            case FIELD -> "field " + ownerInternalName + "." + name + ":" + descriptor;
        };
    }

    private static void requireMemberName(String name) {
        if (name.isEmpty() || name.contains(".") || name.contains("/") || name.contains(";")) {
            throw new IllegalArgumentException("Invalid member name: " + name);
        }
    }

    private static void requireOnly(Set<Access> access, Set<Access> allowed, String what) {
        for (Access requested : access) {
            if (!allowed.contains(requested)) {
                throw new IllegalArgumentException(requested + " does not apply to " + what + "; allowed: " + allowed);
            }
        }
    }
}
