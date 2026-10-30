package dev.plexusmodloader.runtime.discovery;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.BridgeAssessment;
import dev.plexusmodloader.compat.bridge.BridgedEntrypoint;
import dev.plexusmodloader.compat.bridge.CompatibilityStatus;
import dev.plexusmodloader.compat.bridge.ModLoaderBridge;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code quilt_loader.id}, {@code version} and {@code jars} (path texts) from {@code quilt.mod.json}, and treats
 * nested jars with {@code quilt.mod.json} or {@code fabric.mod.json} as mods, like the Quilt bridge.
 */
public final class TestQuiltJarInJarBridge implements ModLoaderBridge {

    @Override
    public String loaderName() {
        return "Quilt";
    }

    @Override
    public String metadataPath() {
        return "quilt.mod.json";
    }

    @Override
    public List<String> nestedMetadataPaths() {
        return List.of("quilt.mod.json", "fabric.mod.json");
    }

    @Override
    public BridgeAssessment assess(String metadata, GameSide side) {
        JsonObject loader = JsonParser.parseString(metadata).getAsJsonObject().getAsJsonObject("quilt_loader");
        List<String> jars = new ArrayList<>();
        if (loader.has("jars")) {
            for (JsonElement entry : loader.getAsJsonArray("jars")) {
                jars.add(entry.getAsString());
            }
        }
        return BridgeAssessment.of(
                loaderName(),
                loader.get("id").getAsString(),
                loader.get("version").getAsString(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                jars,
                CompatibilityStatus.EXPERIMENTAL);
    }

    @Override
    public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) {}
}
