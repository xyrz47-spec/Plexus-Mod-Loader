package dev.plexusmodloader.compat.fabric.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.plexusmodloader.compat.bridge.BridgeLaunchContext;
import dev.plexusmodloader.compat.fabric.mixin.MixinConfigEntry;
import dev.plexusmodloader.compat.quilt.QuiltModDescription;
import dev.plexusmodloader.compat.quilt.QuiltModJsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * A loaded mod of the Fabric family, Fabric or Quilt (ADR-0027 decision 1), as the Fabric bridge's shared machinery sees
 * it: its Mixin configurations, class tweaker (access widener) paths, entrypoint declarations and language adapters.
 * Read from {@code fabric.mod.json} for a Fabric mod and from {@code quilt.mod.json} for a Quilt mod, so that Mixin
 * ({@code FabricMixinBootstrap}), interface injection, the entrypoint store ({@link FabricLoaderRuntime}) and the Quilt
 * Loader API work from one view of both loaders' mods.
 *
 * <p>The bridges assessed these files already; a file that cannot be read again gives no view ({@link #read}).
 *
 * <p>Thread: immutable.
 */
public final class FabricFamilyMod {

    /** Loader name of the Fabric bridge. */
    public static final String FABRIC = "Fabric";

    /** Loader name of the Quilt bridge. */
    public static final String QUILT = "Quilt";

    /**
     * One entrypoint declaration.
     *
     * @param value the class, {@code Class::field} or {@code Class::method}
     * @param adapter the language adapter, {@code default} unless the mod names one
     */
    public record Entrypoint(String value, String adapter) {}

    private final BridgeLaunchContext.LoadedMod mod;
    private final JsonObject fabricModJson;
    private final QuiltModDescription quiltModJson;

    private FabricFamilyMod(BridgeLaunchContext.LoadedMod mod, JsonObject fabricModJson, QuiltModDescription quilt) {
        this.mod = mod;
        this.fabricModJson = fabricModJson;
        this.quiltModJson = quilt;
    }

    /** Whether {@code loader} (a bridge's loader name) is of the Fabric family. */
    public static boolean isFamily(String loader) {
        return loader.equals(FABRIC) || loader.equals(QUILT);
    }

    /**
     * The view of {@code mod}: empty when it is not a Fabric or Quilt mod, or its metadata cannot be read again.
     */
    public static Optional<FabricFamilyMod> read(BridgeLaunchContext.LoadedMod mod) {
        if (mod.runByHost()) {
            return Optional.empty();
        }
        if (mod.loader().equals(FABRIC)) {
            return Optional.ofNullable(FabricLoaderRuntime.readFabricModJson(mod.jar()))
                    .map(json -> new FabricFamilyMod(mod, json, null));
        }
        if (mod.loader().equals(QUILT)) {
            return readEntry(mod.jar(), QuiltModJsonReader.FILE_NAME)
                    .flatMap(text -> QuiltModJsonReader.read(text, false).description())
                    .map(description -> new FabricFamilyMod(mod, null, description));
        }
        return Optional.empty();
    }

    /** The Fabric-family mods of {@code context}, in its order. */
    public static List<FabricFamilyMod> of(BridgeLaunchContext context) {
        List<FabricFamilyMod> mods = new ArrayList<>();
        for (BridgeLaunchContext.LoadedMod mod : context.mods()) {
            read(mod).ifPresent(mods::add);
        }
        return List.copyOf(mods);
    }

    private static Optional<String> readEntry(Path jar, String name) {
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry entry = file.getEntry(name);
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream input = file.getInputStream(entry)) {
                return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // The bridge assessed this jar already; a jar that cannot be read again gives no metadata here.
            return Optional.empty();
        }
    }

    /** The loaded mod. */
    public BridgeLaunchContext.LoadedMod mod() {
        return mod;
    }

    /** Its ID. */
    public String modId() {
        return mod.modId();
    }

    /** Whether it is a Quilt mod ({@code quilt.mod.json}). */
    public boolean isQuilt() {
        return quiltModJson != null;
    }

    /** Its {@code fabric.mod.json}, for a Fabric mod. */
    public Optional<JsonObject> fabricModJson() {
        return Optional.ofNullable(fabricModJson).map(JsonObject::deepCopy);
    }

    /** Its {@code quilt.mod.json} as Quilt Loader reads it, for a Quilt mod. */
    public Optional<QuiltModDescription> quiltModJson() {
        return Optional.ofNullable(quiltModJson);
    }

    /** Its Mixin configurations: {@code mixins} of a Fabric mod, the top-level {@code mixin} of a Quilt mod. */
    public List<MixinConfigEntry> mixinConfigs() {
        return isQuilt()
                ? quiltModJson.mixins()
                : MixinConfigEntry.parse(fabricModJson.get("mixins")).entries();
    }

    /**
     * Paths of its class tweakers in its jar: Fabric's one {@code accessWidener}, or each of Quilt's {@code
     * access_widener} entries.
     */
    public List<String> classTweakers() {
        if (isQuilt()) {
            return quiltModJson.accessWideners();
        }
        JsonElement declared = fabricModJson.get("accessWidener");
        return declared != null && declared.isJsonPrimitive() ? List.of(declared.getAsString()) : List.of();
    }

    /** Its entrypoint declarations: key to declarations, in file order. */
    public Map<String, List<Entrypoint>> entrypoints() {
        Map<String, List<Entrypoint>> declared = new LinkedHashMap<>();
        if (isQuilt()) {
            quiltModJson
                    .entrypoints()
                    .forEach((key, list) -> declared.put(
                            key,
                            list.stream()
                                    .map(entry -> new Entrypoint(entry.value(), entry.adapter()))
                                    .toList()));
            return Collections.unmodifiableMap(declared);
        }
        if (!(fabricModJson.get("entrypoints") instanceof JsonObject entrypoints)) {
            return Map.of();
        }
        for (Map.Entry<String, JsonElement> key : entrypoints.entrySet()) {
            List<JsonElement> elements = new ArrayList<>();
            if (key.getValue() instanceof JsonArray array) {
                array.forEach(elements::add);
            } else {
                elements.add(key.getValue());
            }
            List<Entrypoint> list = new ArrayList<>();
            for (JsonElement element : elements) {
                if (element instanceof JsonObject object && object.get("value") != null) {
                    String adapter =
                            object.has("adapter") ? object.get("adapter").getAsString() : "default";
                    list.add(new Entrypoint(object.get("value").getAsString(), adapter));
                } else if (element.isJsonPrimitive()) {
                    list.add(new Entrypoint(element.getAsString(), "default"));
                }
            }
            declared.put(key.getKey(), List.copyOf(list));
        }
        return Collections.unmodifiableMap(declared);
    }

    /**
     * The Fabric language adapters it declares ({@code languageAdapters}): name to class. A Quilt mod's are Quilt
     * adapters, which the Quilt bridge blocks ({@code PLEXUS-COMPAT-302}), so it has none here.
     */
    public Map<String, String> languageAdapters() {
        Map<String, String> adapters = new LinkedHashMap<>();
        if (fabricModJson != null && fabricModJson.get("languageAdapters") instanceof JsonObject declared) {
            declared.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    adapters.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                }
            });
        }
        return Collections.unmodifiableMap(adapters);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FabricFamilyMod that && mod.equals(that.mod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mod);
    }

    @Override
    public String toString() {
        return mod.loader() + " mod " + mod.modId();
    }
}
