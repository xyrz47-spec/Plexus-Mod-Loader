package dev.polymodloader.game.layer.v26_2;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.game.layer.v26_2.client.TitleScreenModCounts;
import dev.polymodloader.game.version.layer.GameLayer;
import dev.polymodloader.game.version.layer.GameLayerContext;
import java.util.List;

/**
 * Entry point of the Minecraft 26.2 game layer. Loaded by the game class loader (ADR-0010); the
 * transformation rules of {@code Minecraft26_2Adapter} call {@link GameHooks} and
 * {@code client.ClientHooks}, which use the state installed here.
 */
public final class GameLayer26_2 implements GameLayer {

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
