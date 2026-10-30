package dev.plexusmodloader.api.devtools.gametest;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/** One in-game test. */
@ApiStability(StabilityLevel.PREVIEW)
@FunctionalInterface
public interface GameTest {

    /**
     * Starts the test on the server thread. The test passes when it calls
     * {@link GameTestContext#succeed()} (now or from a later tick) and fails when an assertion or the
     * code throws.
     */
    void run(GameTestContext context);
}
