package dev.plexusmodloader.api.resource;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;

/**
 * Lets mods read their own kinds of JSON files from data packs and resource packs, like the game
 * reads recipes or models. Register in entrypoints.
 */
@ApiStability(StabilityLevel.PREVIEW)
public interface ResourceRegistrar {

    /**
     * Reads {@code data/<namespace>/<directory>/**.json} from all enabled data packs (including mod
     * jars) when the server starts and on {@code /reload}; the listener runs on the server thread
     * after the game's own data has loaded.
     *
     * @param directory for example {@code my_mod/spells}; {@code a-z 0-9 _ / . -}
     * @throws IllegalArgumentException for an invalid directory ({@code PLEXUS-RESOURCE-001})
     * @throws IllegalStateException after registration closed ({@code PLEXUS-RESOURCE-002})
     */
    void onDataReload(String directory, ResourceReloadListener listener);

    /**
     * Reads {@code assets/<namespace>/<directory>/**.json} from the enabled resource packs when the
     * client starts and on F3+T; the listener runs on the client thread. Client only; on a dedicated
     * server it is never called.
     */
    void onAssetReload(String directory, ResourceReloadListener listener);

    /**
     * Like {@link #onDataReload}, for files ending in {@code suffix} (for example {@code .png} or
     * {@code .txt}), handed over as bytes.
     */
    void onDataFiles(String directory, String suffix, BinaryReloadListener listener);

    /** Like {@link #onAssetReload}, for files ending in {@code suffix}, as bytes. */
    void onAssetFiles(String directory, String suffix, BinaryReloadListener listener);
}
