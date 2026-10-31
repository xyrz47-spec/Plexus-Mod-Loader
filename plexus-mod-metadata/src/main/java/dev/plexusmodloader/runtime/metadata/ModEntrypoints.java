package dev.plexusmodloader.runtime.metadata;

import java.util.List;

/**
 * Fully qualified entrypoint class names per side, from the {@code entrypoints} object of
 * {@code plexus.mod.json}.
 *
 * @param common classes implementing {@code CommonModInitializer}
 * @param client classes implementing {@code ClientModInitializer}
 * @param server classes implementing {@code DedicatedServerModInitializer}
 */
public record ModEntrypoints(List<String> common, List<String> client, List<String> server) {

    public ModEntrypoints {
        common = List.copyOf(common);
        client = List.copyOf(client);
        server = List.copyOf(server);
    }
}
