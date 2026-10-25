package dev.polymodloader.compat.forge.modlauncher;

import cpw.mods.jarhandling.SecureJar;
import dev.polymodloader.compat.forge.ForeignModIndex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModProvider;

/**
 * The jar of a Poly Mod or other loader's mod as an FML mod file (ADR-0025): its path, name and files. FML did not locate,
 * scan or secure it, so what only FML has for its own files ({@link #getSecureJar}, {@link #getScanResult}, {@link
 * #getProvider}, {@link #setSecurityStatus}) throws {@link UnsupportedOperationException} with the reason, rather than
 * answering with a made-up value.
 *
 * <p>Thread: safe; the jar's file system is opened once, on the first {@link #findResource}.
 */
final class ForeignModFile implements IModFile {

    private final ForeignModIndex.ForeignMod mod;
    private final IModFileInfo info;
    private FileSystem jarFileSystem;

    ForeignModFile(ForeignModIndex.ForeignMod mod, IModFileInfo info) {
        this.mod = mod;
        this.info = info;
    }

    @Override
    public List<IModLanguageProvider> getLoaders() {
        return List.of();
    }

    /** A path in the mod's jar, as FML's own mod file gives one; it may not exist. */
    @Override
    public Path findResource(String... path) {
        return root().resolve(String.join("/", path));
    }

    private synchronized Path root() {
        if (jarFileSystem == null) {
            try {
                jarFileSystem = FileSystems.newFileSystem(mod.jar());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot open " + mod.jar() + " of mod " + mod.modId(), e);
            }
        }
        return jarFileSystem.getRootDirectories().iterator().next();
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
    public SecureJar getSecureJar() {
        throw notFml("has no SecureJar");
    }

    @Override
    public void setSecurityStatus(SecureJar.Status status) {
        throw notFml("has no security status");
    }

    @Override
    public List<IModInfo> getModInfos() {
        return info.getMods();
    }

    @Override
    public ModFileScanData getScanResult() {
        throw notFml("was not scanned by FML");
    }

    @Override
    public String getFileName() {
        return mod.jar().getFileName().toString();
    }

    @Override
    public IModProvider getProvider() {
        throw notFml("was not located by FML");
    }

    @Override
    public IModFileInfo getModFileInfo() {
        return info;
    }

    private UnsupportedOperationException notFml(String what) {
        return new UnsupportedOperationException(mod.loader() + " mod " + mod.modId() + " " + what
                + ": Poly Mod Loader runs it beside Forge, and FML's mod list shows it for presence queries only"
                + " (ADR-0025)");
    }
}
