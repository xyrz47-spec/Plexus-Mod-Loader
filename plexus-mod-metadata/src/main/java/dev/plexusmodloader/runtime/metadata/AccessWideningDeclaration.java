package dev.plexusmodloader.runtime.metadata;

import java.util.List;
import java.util.Set;

/**
 * One entry of {@code accessWidenings} in {@code poly.mod.json}. The metadata module checks the shape;
 * the runtime turns it into an engine widening and the planner checks the target against the game.
 *
 * @param minecraft exact Minecraft version the target was verified against
 * @param sides {@code client} and/or {@code dedicated_server}
 * @param owner the class, or the class declaring the member (JVM internal name)
 * @param kind {@code class}, {@code method} or {@code field}
 * @param name the member name; empty for a class
 * @param descriptor the member's JVM descriptor; empty for a class
 * @param access {@code accessible}, {@code extendable} and/or {@code mutable}
 * @param abortOnFailure true for {@code "onFailure": "abort_startup"} (the default), false for {@code
 *     "skip"}
 */
public record AccessWideningDeclaration(
        String minecraft,
        List<String> sides,
        String owner,
        String kind,
        String name,
        String descriptor,
        Set<String> access,
        boolean abortOnFailure) {

    /** The kinds this Poly Mod Loader version understands. */
    public static final Set<String> KINDS = Set.of("class", "method", "field");

    /** The access words this Poly Mod Loader version understands. */
    public static final Set<String> ACCESS = Set.of("accessible", "extendable", "mutable");

    public AccessWideningDeclaration {
        sides = List.copyOf(sides);
        access = Set.copyOf(access);
    }
}
