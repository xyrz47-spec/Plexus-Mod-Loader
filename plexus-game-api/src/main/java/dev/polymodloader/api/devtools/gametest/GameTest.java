package dev.polymodloader.api.devtools.gametest;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;

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
