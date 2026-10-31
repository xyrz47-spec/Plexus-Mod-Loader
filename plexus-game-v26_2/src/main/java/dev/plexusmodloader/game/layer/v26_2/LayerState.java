package dev.plexusmodloader.game.layer.v26_2;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.version.layer.ChannelEndpoint;
import dev.plexusmodloader.game.version.layer.ConfigurationEndpoint;
import dev.plexusmodloader.game.version.layer.GameLayerContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** State the game layer shares between its hooks. Written once at start, read from game threads. */
public final class LayerState {

    private static volatile GameLayerContext context;
    private static volatile Map<ResourceId, ChannelEndpoint> channels = Map.of();
    private static volatile Map<ResourceId, ConfigurationEndpoint> configurationExchanges = Map.of();
    private static volatile Optional<GeneratedDataPack.Written> generatedDataPack = Optional.empty();

    private LayerState() {}

    static void install(GameLayerContext layerContext) {
        context = layerContext;
        Map<ResourceId, ChannelEndpoint> byId = new LinkedHashMap<>();
        byId.put(SyncChannel.ID, new SyncChannel());
        layerContext.declarations().channels().forEach(channel -> byId.put(channel.id(), channel));
        channels = Map.copyOf(byId);
        Map<ResourceId, ConfigurationEndpoint> exchanges = new LinkedHashMap<>();
        layerContext
                .declarations()
                .configurationExchanges()
                .forEach(exchange -> exchanges.put(exchange.id(), exchange));
        configurationExchanges = Collections.unmodifiableMap(exchanges);
        layerContext.declarations().bindTransport(new Networking.Transport(layerContext.side()));
        // Only client registrations declare dynamic textures, so the client class loads only there.
        layerContext
                .declarations()
                .dynamicTextures()
                .forEach(declaration -> declaration
                        .bind()
                        .accept(pixels -> dev.plexusmodloader.game.layer.v26_2.client.DynamicTextures.upload(
                                declaration, pixels)));
        try {
            // The generated pack is a known pack (ModPacks), so its test instances and biomes keep
            // worlds stable.
            generatedDataPack = GeneratedDataPack.write(layerContext.declarations());
        } catch (IOException e) {
            throw new UncheckedIOException("Plexus Mod Loader could not write its generated data pack", e);
        }
    }

    /** The context, or empty when Plexus Mod Loader started without this layer (unit tests of other modules). */
    public static Optional<GameLayerContext> context() {
        return Optional.ofNullable(context);
    }

    /** Declared channels by ID. */
    public static Map<ResourceId, ChannelEndpoint> channels() {
        return channels;
    }

    /** Declared configuration exchanges by ID, in declaration order. */
    public static Map<ResourceId, ConfigurationEndpoint> configurationExchanges() {
        return configurationExchanges;
    }

    /** Directory of the data pack Plexus Mod Loader generates (game tests, biomes), if any. */
    static Optional<Path> generatedDataPack() {
        return generatedDataPack.map(GeneratedDataPack.Written::root);
    }

    /** Version of the generated pack: changes whenever any generated file changes. */
    static String generatedDataPackVersion() {
        return generatedDataPack.map(GeneratedDataPack.Written::version).orElse("0");
    }
}
