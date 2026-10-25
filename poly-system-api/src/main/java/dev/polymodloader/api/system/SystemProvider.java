package dev.polymodloader.api.system;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

/**
 * Creates the implementation of one system. A mod that replaces a system declares its provider
 * class in {@code poly.mod.json} under {@code replacesSystems}; the class needs a public
 * no-argument constructor.
 *
 * <p><b>Timing:</b> called once on the main thread after all mods are resolved and before any mod
 * entrypoint runs.
 *
 * <p><b>Failure:</b> an exception, a {@code null} result, or a result that does not implement the
 * system's contract stops the launch ({@code POLY-SYSTEM-003}).
 *
 * @param <T> the system contract
 */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface SystemProvider<T extends PolySystem> {

    /** Creates the system implementation. */
    T create(SystemContext context);
}
