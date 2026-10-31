package dev.plexusmodloader.game.version;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Locale;
import java.util.Optional;

/**
 * Which Minecraft program Plexus Mod Loader starts. Several targets can share one {@link GameSide}: the game test
 * server is a dedicated server that runs Mojang's GameTest framework and exits.
 */
@ApiStability(StabilityLevel.INTERNAL)
public enum GameLaunchTarget {
    /** The game client. */
    CLIENT(GameSide.CLIENT),
    /** The dedicated server. */
    DEDICATED_SERVER(GameSide.DEDICATED_SERVER),
    /** Mojang's headless game test server: creates a world, runs GameTests, saves, and exits. */
    GAME_TEST_SERVER(GameSide.DEDICATED_SERVER);

    private final GameSide side;

    GameLaunchTarget(GameSide side) {
        this.side = side;
    }

    /** The side mods see for this target. */
    public GameSide side() {
        return side;
    }

    /** Spelling used on the command line, e.g. {@code game_test_server}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses {@link #id()}. */
    public static Optional<GameLaunchTarget> parse(String id) {
        for (GameLaunchTarget target : values()) {
            if (target.id().equals(id)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }
}
