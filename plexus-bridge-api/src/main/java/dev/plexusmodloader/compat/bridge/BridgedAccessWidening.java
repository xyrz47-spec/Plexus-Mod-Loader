package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One access widening a foreign mod asks for, already translated by its bridge into Minecraft's own names
 * (Fabric class tweakers, ADR-0018). The runtime checks the target against the game jar before the mod
 * loads and turns the widening into a transform-engine widening; it means exactly what Poly Mod Loader's {@code
 * accessWidenings} in {@code poly.mod.json} mean (ADR-0013).
 *
 * @param source where the widening was declared, as {@code file:line}, for diagnostics
 * @param owner the class, or the class declaring the member, as a JVM internal name
 * @param kind what is widened
 * @param name the member name; empty for {@link Kind#CLASS}
 * @param descriptor the member's JVM descriptor; empty for {@link Kind#CLASS}
 * @param access what to allow; not empty
 */
@ApiStability(StabilityLevel.INTERNAL)
public record BridgedAccessWidening(
        String source, String owner, Kind kind, String name, String descriptor, Set<Access> access) {

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

    public BridgedAccessWidening {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        if (access.isEmpty()) {
            throw new IllegalArgumentException("an access widening must allow something");
        }
        access = Set.copyOf(EnumSet.copyOf(access));
    }

    /** A readable name of the target, for example {@code field net/minecraft/A.f:I}. */
    public String describeTarget() {
        return switch (kind) {
            case CLASS -> "class " + owner;
            case METHOD -> "method " + owner + "." + name + descriptor;
            case FIELD -> "field " + owner + "." + name + ":" + descriptor;
        };
    }
}
