package dev.plexusmodloader.game.layer.v26_3;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.game.layer.v26_3.client.TitleScreenModCounts;
import dev.plexusmodloader.game.version.layer.GameLayer;
import dev.plexusmodloader.game.version.layer.GameLayerContext;
import java.util.List;

/**
 * Entry point of the Minecraft 26.3 game layer. Loaded by the game class loader (ADR-0010); the
 * transformation rules of {@code Minecraft26_3Adapter} call {@link GameHooks} and
 * {@code client.ClientHooks}, which use the state installed here.
 */
public final class GameLayer26_3 implements GameLayer {

    @Override
    public void start(GameLayerContext context) {
        LayerState.install(context);
    }

    /** The title screen's mod counts per loader ({@link TitleScreenModCounts}), on the client. */
    @Override
    public List<String> brandingLines() {
        boolean client = LayerState.context()
                .map(context -> context.side() == GameSide.CLIENT)
                .orElse(false);
        return client ? TitleScreenModCounts.lines() : List.of();
    }
}
