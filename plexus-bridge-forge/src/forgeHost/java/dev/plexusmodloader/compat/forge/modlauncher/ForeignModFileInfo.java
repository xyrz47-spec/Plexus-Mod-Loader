package dev.plexusmodloader.compat.forge.modlauncher;

import dev.plexusmodloader.compat.forge.ForeignModIndex;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

/**
 * A Poly Mod or other loader's mod as an FML mod file (ADR-0025): one mod, its version as the file's version, no
 * language loader, no resource or data pack (its own loader serves its resources), and no {@code mods.toml}
 * configuration.
 *
 * <p>Thread: immutable.
 */
final class ForeignModFileInfo implements IModFileInfo {

    /** A configuration without elements, as FML's for a file without {@code mods.toml} values. */
    static final IConfigurable NO_CONFIG = new IConfigurable() {
        @Override
        public <T> Optional<T> getConfigElement(String... key) {
            return Optional.empty();
        }

        @Override
        public List<? extends IConfigurable> getConfigList(String... key) {
            return List.of();
        }
    };

    private final ForeignModIndex.ForeignMod mod;
    private final ForeignModInfo info;
    private final ForeignModFile file;

    ForeignModFileInfo(ForeignModIndex.ForeignMod mod) {
        this.mod = mod;
        this.info = new ForeignModInfo(mod, this);
        this.file = new ForeignModFile(mod, this);
    }

    @Override
    public List<IModInfo> getMods() {
        return List.of(info);
    }

    @Override
    public List<LanguageSpec> requiredLanguageLoaders() {
        return List.of();
    }

    @Override
    public boolean showAsResourcePack() {
        return false;
    }

    @Override
    public boolean showAsDataPack() {
        return false;
    }

    @Override
    public boolean isClientSideOnly() {
        return false;
    }

    @Override
    public Map<String, Object> getFileProperties() {
        return Map.of();
    }

    @Override
    public String getLicense() {
        return "";
    }

    /** FML names a mod file's module; a Poly Mod or other loader's mod is in no module, so this is its mod ID. */
    @Override
    public String moduleName() {
        return mod.modId();
    }

    @Override
    public String versionString() {
        return mod.version();
    }

    @Override
    public List<String> usesServices() {
        return List.of();
    }

    @Override
    public IModFile getFile() {
        return file;
    }

    @Override
    public IConfigurable getConfig() {
        return NO_CONFIG;
    }

    @Override
    public String toString() {
        return mod.loader() + " mod " + mod.modId() + " " + mod.version() + " ("
                + mod.jar().getFileName() + ")";
    }
}
