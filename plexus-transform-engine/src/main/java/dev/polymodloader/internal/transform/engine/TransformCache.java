package dev.polymodloader.internal.transform.engine;

import java.util.Optional;

/**
 * Stores transformed class files so later launches can skip the transformation. Keys are computed
 * by {@link TransformCacheKey} from everything that can change the output, so a stale entry is never
 * found; implementations must still verify entries and treat a damaged entry as a miss.
 */
public interface TransformCache {

    /** Returns the verified transformed bytes for {@code key}, or empty on a miss or a damaged entry. */
    Optional<byte[]> get(TransformCacheKey key);

    /** Stores {@code transformed} under {@code key}. Failures are reported but never fail class loading. */
    void put(TransformCacheKey key, byte[] transformed);
}
