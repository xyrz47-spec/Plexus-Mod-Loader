package dev.polymodloader.compat.forge.fml;

import dev.polymodloader.compat.forge.ForeignModIndex;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * A Poly Mod or other loader's mod as a NeoForge FML mod (ADR-0025, ADR-0026): its ID, name and version; no
 * dependencies, features or URLs, since FML does not resolve or show it. It has no FML language loader ({@link
 * #getLoader} throws): FML never constructs it.
 *
 * <p>Thread: immutable.
 */
final class ForeignModInfo implements IModInfo {

    private final ForeignModIndex.ForeignMod mod;
    private final IModFileInfo owner;
    private final ArtifactVersion version;

    ForeignModInfo(ForeignModIndex.ForeignMod mod, IModFileInfo owner) {
        this.mod = mod;
        this.owner = owner;
        // As FML reads a neoforge.mods.toml version: Maven's version parsing, which accepts any text.
        this.version = new DefaultArtifactVersion(mod.version());
    }

    @Override
    public IModFileInfo getOwningFile() {
        return owner;
    }

    @Override
    public IModLanguageLoader getLoader() {
        throw new UnsupportedOperationException(mod.loader() + " mod " + mod.modId() + " has no FML language loader:"
                + " Poly Mod Loader runs it beside NeoForge, and FML's mod list shows it for presence queries only"
                + " (ADR-0025)");
    }

    @Override
    public String getModId() {
        return mod.modId();
    }

    @Override
    public String getDisplayName() {
        return mod.displayName();
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public ArtifactVersion getVersion() {
        return version;
    }

    @Override
    public List<? extends ModVersion> getDependencies() {
        return List.of();
    }

    @Override
    public List<? extends ForgeFeature.Bound> getForgeFeatures() {
        return List.of();
    }

    @Override
    public String getNamespace() {
        return mod.modId();
    }

    @Override
    public Map<String, Object> getModProperties() {
        return Map.of();
    }

    @Override
    public Optional<URL> getUpdateURL() {
        return Optional.empty();
    }

    @Override
    public Optional<URL> getModURL() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getLogoFile() {
        return Optional.empty();
    }

    @Override
    public boolean getLogoBlur() {
        return true;
    }

    @Override
    public IConfigurable getConfig() {
        return ForeignModFileInfo.NO_CONFIG;
    }

    @Override
    public String toString() {
        return mod.modId() + " " + mod.version();
    }
}
