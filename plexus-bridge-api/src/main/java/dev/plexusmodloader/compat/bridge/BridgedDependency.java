package dev.plexusmodloader.compat.bridge;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * One relationship a foreign mod declares with another mod, in its own loader's terms. The runtime checks it against
 * every installed mod of every loader ({@code CrossLoaderModResolver}) after discovery, on the launch thread.
 *
 * <p>Most relationships name one mod ({@link #members()} is empty). A <em>group</em> ({@code members} not empty, Quilt's
 * dependency arrays) has no {@code modId} of its own: a {@link Kind#REQUIRED} or {@link Kind#IF_PRESENT} group is met
 * when any member is met, a {@link Kind#BREAKS} group breaks only when every member matches. Members may be groups
 * themselves.
 *
 * @param modId the other mod's ID, as the foreign loader names it (may contain {@code -}); {@code ""} for a group
 * @param kind what the relationship means
 * @param requirement the requirement as written in the metadata, for messages (for example {@code >=0.30.0}, {@code
 *     any of [a, b]}, {@code unless c})
 * @param accepts whether a version string satisfies the requirement, with the foreign loader's rules; not used for a
 *     group
 * @param members the relationships of a group; empty for a relationship that names one mod
 * @param unless relationships that, when any of them is met as a requirement (Quilt {@code unless}), make this one not
 *     apply
 * @param skipWhenOtherSideOnly whether the relationship does not apply when every installed mod with the ID is left out
 *     only because it is for the other side (Quilt Loader ignores such dependencies)
 */
@ApiStability(StabilityLevel.INTERNAL)
public record BridgedDependency(
        String modId,
        Kind kind,
        String requirement,
        Predicate<String> accepts,
        List<BridgedDependency> members,
        List<BridgedDependency> unless,
        boolean skipWhenOtherSideOnly) {

    /** How a declared relationship affects loading. */
    public enum Kind {
        /** The mod cannot run without a matching version of the other mod. */
        REQUIRED,
        /** The mod works without the other mod; nothing is checked. */
        OPTIONAL,
        /** The mod must not run together with a matching version of the other mod. */
        BREAKS,
        /** Running together may misbehave; reported as a warning only. */
        CONFLICTS,
        /**
         * Checked only when a mod with the ID is installed: it must then be a loadable matching version (Quilt {@code
         * optional}). Without one, nothing is required.
         */
        IF_PRESENT
    }

    public BridgedDependency {
        members = List.copyOf(members);
        unless = List.copyOf(unless);
    }

    /** A relationship with one mod, without {@code unless} conditions. */
    public BridgedDependency(String modId, Kind kind, String requirement, Predicate<String> accepts) {
        this(modId, kind, requirement, accepts, List.of(), List.of(), false);
    }

    /**
     * A group of relationships ({@code members} not empty): met when any member is met, or for {@link Kind#BREAKS},
     * breaking only when every member matches.
     */
    public static BridgedDependency group(Kind kind, String requirement, List<BridgedDependency> members) {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("A dependency group needs at least one member");
        }
        return new BridgedDependency("", kind, requirement, version -> false, members, List.of(), false);
    }

    /** Whether this is a group ({@link #members()} not empty). */
    public boolean isGroup() {
        return !members.isEmpty();
    }

    /** This relationship with {@code conditions} as its {@code unless} relationships. */
    public BridgedDependency withUnless(List<BridgedDependency> conditions) {
        return new BridgedDependency(modId, kind, requirement, accepts, members, conditions, skipWhenOtherSideOnly);
    }

    /** This relationship, not applying when every installed mod with the ID is for the other side only. */
    public BridgedDependency withSkipWhenOtherSideOnly() {
        return new BridgedDependency(modId, kind, requirement, accepts, members, unless, true);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase(Locale.ROOT) + " " + (isGroup() ? members.toString() : modId) + " "
                + requirement;
    }
}
