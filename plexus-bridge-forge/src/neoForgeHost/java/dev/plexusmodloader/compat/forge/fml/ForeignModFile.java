package dev.plexusmodloader.compat.forge.fml;

import dev.plexusmodloader.compat.forge.ForeignModIndex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * The jar of a Plexus Mod or other loader's mod as a NeoForge FML mod file (ADR-0025, ADR-0026): its ID, path, name and
 * contents. FML did not locate or scan it, so {@link #getScanResult} throws {@link UnsupportedOperationException} with
 * the reason, rather than answering with a made-up value.
 *
 * <p>Thread: safe; the jar's contents are opened once, on the first {@link #getContents}.
 */
final class ForeignModFile implements IModFile {

    private final ForeignModIndex.ForeignMod mod;
    private final IModFileInfo info;
    private JarContents contents;

    ForeignModFile(ForeignModIndex.ForeignMod mod, IModFileInfo info) {
        this.mod = mod;
        this.info = info;
    }

    /** As FML names a mod file without a module: the mod's ID. */
    @Override
    public String getId() {
        return mod.modId();
    }

    @Override
    public synchronized JarContents getContents() {
        if (contents == null) {
            try {
                contents = JarContents.ofPath(mod.jar());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot open " + mod.jar() + " of mod " + mod.modId(), e);
            }
        }
        return contents;
    }

    @Override
    public Supplier<Map<String, Object>> getSubstitutionMap() {
        return Map::of;
    }

    @Override
    public Type getType() {
        return Type.MOD;
    }

    @Override
    public Path getFilePath() {
        return mod.jar();
    }

    @Override
    public List<IModInfo> getModInfos() {
        return info.getMods();
    }

    @Override
    public ModFileScanData getScanResult() {
        throw new UnsupportedOperationException(mod.loader() + " mod " + mod.modId() + " was not scanned by FML:"
                + " Plexus Mod Loader runs it beside NeoForge, and FML's mod list shows it for presence queries only"
                + " (ADR-0025)");
    }

    @Override
    public String getFileName() {
        return mod.jar().getFileName().toString();
    }

    @Override
    public ModFileDiscoveryAttributes getDiscoveryAttributes() {
        return ModFileDiscoveryAttributes.DEFAULT;
    }

    @Override
    public IModFileInfo getModFileInfo() {
        return info;
    }
}
