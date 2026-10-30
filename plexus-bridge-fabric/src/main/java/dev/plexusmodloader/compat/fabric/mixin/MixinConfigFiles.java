package dev.plexusmodloader.compat.fabric.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.plexusmodloader.compat.bridge.ModJarContents;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Reads a Mixin configuration file from a mod jar. */
final class MixinConfigFiles {

    private MixinConfigFiles() {}

    /** The configuration's root object, or empty when it is missing, unreadable, or not a JSON object. */
    static Optional<JsonObject> read(String path, ModJarContents jar) {
        try {
            Optional<byte[]> bytes = jar.read(path);
            if (bytes.isEmpty()) {
                return Optional.empty();
            }
            JsonElement root = JsonParser.parseString(new String(bytes.get(), StandardCharsets.UTF_8));
            return root instanceof JsonObject object ? Optional.of(object) : Optional.empty();
        } catch (UncheckedIOException | JsonParseException e) {
            return Optional.empty();
        }
    }
}
