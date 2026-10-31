package dev.plexusmodloader.api.devtools.gametest;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Registers in-game tests that run on Mojang's game test server ({@code net.minecraft.gametest.Main},
 * for example through the {@code plexusScenarios} Gradle tasks). Each test gets a fresh empty area; its
 * ID is {@code <mod id>:<name>}, so {@code --tests <mod id>:*} runs all of a mod's tests. In a
 * normal world they also run with {@code /test run <mod id>:<name>}.
 *
 * <p><b>When:</b> inside common entrypoints.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface GameTestRegistrar {

    /** Registers a required test that must finish within 100 ticks. */
    default void register(String name, GameTest test) {
        register(name, 100, test);
    }

    /**
     * Registers a required test.
     *
     * @param maxTicks ticks before the test fails with a timeout
     * @throws IllegalArgumentException for an invalid or already used name ({@code PLEXUS-GAMETEST-001})
     * @throws IllegalStateException after registration closed ({@code PLEXUS-GAMETEST-002})
     */
    default void register(String name, int maxTicks, GameTest test) {
        register(name, maxTicks, TestArea.SINGLE_BLOCK, test);
    }

    /**
     * Registers a required test that gets {@code area} to itself.
     *
     * @throws IllegalArgumentException for an invalid or already used name ({@code PLEXUS-GAMETEST-001})
     * @throws IllegalStateException after registration closed ({@code PLEXUS-GAMETEST-002})
     */
    void register(String name, int maxTicks, TestArea area, GameTest test);
}
