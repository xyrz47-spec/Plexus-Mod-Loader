package dev.polymodloader.game.layer.v1_21_11;

import dev.polymodloader.api.resource.ResourceId;
import dev.polymodloader.game.version.layer.NativeDeclarations.ReloadListenerDeclaration;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the files that mods asked for while the game reloads its packs (in the background), and
 * hands them to the mods on the thread that applies reloads (server or client thread).
 */
public final class ModReloadListener
        extends SimplePreparableReloadListener<Map<ReloadListenerDeclaration, Map<ResourceId, byte[]>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    private final List<ReloadListenerDeclaration> declarations;

    public ModReloadListener(List<ReloadListenerDeclaration> declarations) {
        this.declarations = List.copyOf(declarations);
    }

    @Override
    protected Map<ReloadListenerDeclaration, Map<ResourceId, byte[]>> prepare(
            ResourceManager manager, ProfilerFiller profiler) {
        Map<ReloadListenerDeclaration, Map<ResourceId, byte[]>> files = new LinkedHashMap<>();
        for (ReloadListenerDeclaration declaration : declarations) {
            Map<ResourceId, byte[]> found = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
            String prefix = declaration.directory() + "/";
            manager.listResources(declaration.directory(), id -> id.getPath().endsWith(declaration.suffix()))
                    .forEach((Identifier id, Resource resource) -> {
                        String path = id.getPath()
                                .substring(
                                        prefix.length(),
                                        id.getPath().length()
                                                - declaration.suffix().length());
                        found.put(ResourceId.of(id.getNamespace(), path), read(resource, id));
                    });
            files.put(declaration, found);
        }
        return files;
    }

    private static byte[] read(Resource resource, Identifier id) {
        try (InputStream input = resource.open()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("PLEXUS-RESOURCE-003 cannot read " + id, e);
        }
    }

    @Override
    protected void apply(
            Map<ReloadListenerDeclaration, Map<ResourceId, byte[]>> files,
            ResourceManager manager,
            ProfilerFiller profiler) {
        files.forEach((declaration, found) -> {
            try {
                Map<ResourceId, byte[]> copies = new LinkedHashMap<>();
                found.forEach((id, bytes) -> copies.put(id, bytes.clone()));
                declaration.listener().onReload(copies);
            } catch (RuntimeException e) {
                LOGGER.error(
                        "[Poly Mod Loader] PLEXUS-RESOURCE-003 reload listener of {} for {} failed",
                        declaration.ownerModId(),
                        declaration.directory(),
                        e);
            }
        });
    }
}
