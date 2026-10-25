package dev.polymodloader.internal.transform.engine;

import java.util.Optional;

/**
 * Reads untransformed class bytes without loading or defining the class. The planner uses it to
 * validate rules against the real bytecode before the game starts.
 */
@FunctionalInterface
public interface ClassBytesSource {

    /** Returns the class file for {@code internalName} (e.g. {@code a/b/C}), or empty if absent. */
    Optional<byte[]> read(String internalName);
}
