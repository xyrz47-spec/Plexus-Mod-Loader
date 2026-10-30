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

/** Reads {@code id}, {@code version} and {@code jars} from {@code fabric.mod.json}, like the Fabric bridge. */
public final class TestJarInJarBridge implements ModLoaderBridge {

    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public String metadataPath() {
        return "fabric.mod.json";
    }

    @Override
    public BridgeAssessment assess(String metadata, GameSide side) {
        JsonObject root = JsonParser.parseString(metadata).getAsJsonObject();
        List<String> jars = new ArrayList<>();
        if (root.has("jars")) {
            for (JsonElement entry : root.getAsJsonArray("jars")) {
                jars.add(entry.getAsJsonObject().get("file").getAsString());
            }
        }
        return BridgeAssessment.of(
                loaderName(),
                root.get("id").getAsString(),
                root.get("version").getAsString(),
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
