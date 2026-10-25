package dev.polymodloader.compat.bridge;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;

/**
 * An interface a foreign mod adds to a game class (ADR-0020): a Fabric class tweaker {@code inject-interface} line,
 * which the bridge applies when the class loads, or an interface a mixin class implements, which Mixin adds to its
 * target. The runtime's link check treats every such interface as a supertype of the class when it resolves the mods'
 * method references; for the lines the bridge applies it also verifies before the mod loads that the class exists in
 * this game and the interface in an installed jar.
 *
 * @param source where it was declared, as {@code file:line}, for diagnostics
 * @param target the game class, as a JVM internal name
 * @param interfaceName the interface, as a JVM internal name without generic arguments
 * @param appliedByBridge {@code true} for a line the bridge applies; {@code false} for an interface the loader's own
 *     transformer adds (Mixin), which may target optional classes and is not checked
 */
@ApiStability(StabilityLevel.INTERNAL)
public record BridgedInterfaceInjection(String source, String target, String interfaceName, boolean appliedByBridge) {

    public BridgedInterfaceInjection {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(interfaceName, "interfaceName");
    }

    /** A line the bridge applies. */
    public BridgedInterfaceInjection(String source, String target, String interfaceName) {
        this(source, target, interfaceName, true);
    }

    /** A readable description, for example {@code net/minecraft/A implements a/mod/Api}. */
    public String describe() {
        return target + " implements " + interfaceName;
    }
}
