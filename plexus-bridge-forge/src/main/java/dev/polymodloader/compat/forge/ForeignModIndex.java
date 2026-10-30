package dev.polymodloader.compat.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * The mods Poly Mod Loader runs beside Forge (Poly Mods and other loaders' mods), as FML's mod list answers for them
 * (ADR-0025): Forge mods ask {@code ModList.getModFileById(id)} or {@code ModList.isLoaded(id)} whether a mod is
 * installed, and a Fabric mod they work with (Physics Mod with Sodium) is installed.
 *
 * <p>Forge keeps its own IDs: a mod Forge runs ({@link BridgeLaunchContext.LoadedMod#runByHost()}) or whose ID or
 * provided ID is a Forge mod's is not answered for here, and FML's own answer is asked first anyway. A mod's own ID wins
 * over another mod's provided ID.
 *
 * <p>Thread: immutable once built.
 */
@ApiStability(StabilityLevel.INTERNAL)
public final class ForeignModIndex {

    /** No other mods: FML's mod list is left as it is. */
    public static final ForeignModIndex EMPTY = new ForeignModIndex(Map.of(), List.of());

    /**
     * One mod as FML's mod list shows it.
     *
     * @param loader {@code Fabric}, {@code Poly Mod Loader}, …
     * @param version its version text, as its own loader reads it
     * @param displayName its name ({@code fabric.mod.json}'s {@code name}, {@code quilt.mod.json}'s {@code
     *     quilt_loader.metadata.name}), or the mod ID
     * @param jar its jar (for a nested mod, the extracted copy)
     * @param ids the IDs it answers to: its own, then the provided ones it keeps
     */
    public record ForeignMod(
            String loader, String modId, String version, String displayName, Path jar, List<String> ids) {
        public ForeignMod {
            ids = List.copyOf(ids);
        }
    }

    private final Map<String, ForeignMod> byId;
    private final List<ForeignMod> mods;

    private ForeignModIndex(Map<String, ForeignMod> byId, List<ForeignMod> mods) {
        this.byId = Map.copyOf(byId);
        this.mods = List.copyOf(mods);
    }

    /**
     * Indexes {@code mods} by ID and provided ID.
     *
     * @param mods the mods Poly Mod Loader runs, in entrypoint order
     * @param hostIds the IDs of the mods Forge runs, which stay Forge's
     */
    public static ForeignModIndex of(List<BridgeLaunchContext.LoadedMod> mods, Collection<String> hostIds) {
        List<BridgeLaunchContext.LoadedMod> shown = mods.stream()
                .filter(mod -> !mod.runByHost() && !hostIds.contains(mod.modId()))
                .toList();
        Map<String, BridgeLaunchContext.LoadedMod> owners = new LinkedHashMap<>();
        for (BridgeLaunchContext.LoadedMod mod : shown) {
            owners.putIfAbsent(mod.modId(), mod);
        }
        for (BridgeLaunchContext.LoadedMod mod : shown) {
            for (String provided : mod.providedIds()) {
                if (!hostIds.contains(provided)) {
                    owners.putIfAbsent(provided, mod);
                }
            }
        }
        Map<String, ForeignMod> byId = new LinkedHashMap<>();
        List<ForeignMod> indexed = new ArrayList<>();
        for (BridgeLaunchContext.LoadedMod mod : shown) {
            if (owners.get(mod.modId()) != mod) {
                continue; // a second mod with the same ID: the first one answers
            }
            List<String> ids = new ArrayList<>(List.of(mod.modId()));
            mod.providedIds().stream()
                    .filter(provided -> owners.get(provided) == mod && !ids.contains(provided))
                    .forEach(ids::add);
            ForeignMod foreign =
                    new ForeignMod(mod.loader(), mod.modId(), mod.version(), displayName(mod), mod.jar(), ids);
            indexed.add(foreign);
            ids.forEach(id -> byId.put(id, foreign));
        }
        return new ForeignModIndex(byId, indexed);
    }

    /** The mod answering to {@code id}, its own or a provided one. */
    public Optional<ForeignMod> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Every indexed mod, once each, in entrypoint order. */
    public List<ForeignMod> mods() {
        return mods;
    }

    public boolean isEmpty() {
        return mods.isEmpty();
    }

    /**
     * The mod's name. Poly Mod Loader knows a Fabric or Quilt mod's ID only, so its {@code fabric.mod.json} ({@code
     * name}) or {@code quilt.mod.json} ({@code quilt_loader.metadata.name}, ADR-0027) gives the name; the ID when it has
     * none, as in Fabric Loader and Quilt Loader.
     */
    private static String displayName(BridgeLaunchContext.LoadedMod mod) {
        boolean fabric = mod.loader().equals("Fabric");
        boolean quilt = mod.loader().equals("Quilt");
        if (!(fabric || quilt) || !mod.name().equals(mod.modId()) || mod.jar() == null) {
            return mod.name();
        }
        try (JarFile jar = new JarFile(mod.jar().toFile())) {
            ZipEntry entry = jar.getEntry(fabric ? "fabric.mod.json" : "quilt.mod.json");
            if (entry == null) {
                return mod.name();
            }
            try (InputStream input = jar.getInputStream(entry)) {
                JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (quilt) {
                    json = json.get("quilt_loader") instanceof JsonObject loader
                                    && loader.get("metadata") instanceof JsonObject metadata
                            ? metadata
                            : new JsonObject();
                }
                return json.has("name") && json.get("name").isJsonPrimitive()
                        ? json.get("name").getAsString()
                        : mod.name();
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            // The bridge read this file already; if it cannot be read again, the ID is the name.
            return mod.name();
        }
    }
}
