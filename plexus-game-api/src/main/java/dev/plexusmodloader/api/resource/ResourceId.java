package dev.plexusmodloader.api.resource;

import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A namespaced identifier such as {@code minecraft:stone} or {@code my_mod:copper_bricks}. Names use
 * Minecraft's rules: the namespace may contain {@code a-z 0-9 _ - .}, the path additionally
 * {@code /}. Invalid names are rejected when the identifier is created, not when the game uses it.
 *
 * @param namespace mod ID or {@code minecraft}
 * @param path name inside the namespace
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ResourceId(String namespace, String path) implements Comparable<ResourceId> {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.\\-/]+");

    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace '" + namespace + "': use a-z 0-9 _ - .");
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid path '" + path + "': use a-z 0-9 _ - . /");
        }
    }

    /** Creates {@code namespace:path}. */
    public static ResourceId of(String namespace, String path) {
        return new ResourceId(namespace, path);
    }

    /** An identifier in the {@code minecraft} namespace. */
    public static ResourceId minecraft(String path) {
        return new ResourceId("minecraft", path);
    }

    /**
     * Parses {@code namespace:path}; text without a colon is in the {@code minecraft} namespace, as in
     * the game's commands and data files.
     */
    public static ResourceId parse(String text) {
        int colon = text.indexOf(':');
        return colon < 0 ? minecraft(text) : new ResourceId(text.substring(0, colon), text.substring(colon + 1));
    }

    @Override
    public int compareTo(ResourceId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
