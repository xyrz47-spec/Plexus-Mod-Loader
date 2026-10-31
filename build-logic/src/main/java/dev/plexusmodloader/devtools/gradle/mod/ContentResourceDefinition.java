package dev.plexusmodloader.devtools.gradle.mod;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One block or item whose standard resource files {@link ModResourceGenerator} writes, declared in a
 * mod's build script with {@code plexusResources { item("ruby"); block("ruby_block") }}.
 *
 * @param kind whether this is an item or a block (a block also gets its block item's files)
 * @param name content path, the same one the mod's code registers ({@code "ruby_block"})
 * @param displayName English name for {@code lang/en_us.json}; {@code null} derives it from {@code name}
 * @param texture texture ID ({@code "ruby_mod:block/ruby_block"}, or {@code "block/ruby_block"} in the mod's
 *     namespace); {@code null} means {@code <mod>:item/<name>} or {@code <mod>:block/<name>}
 * @param minedWith for a block, the tool tag it joins ({@code "pickaxe"}, {@code "axe"}, {@code "shovel"},
 *     {@code "hoe"}); {@code null} adds it to none
 * @param dropsSelf for a block, whether its loot table drops the block item when broken
 */
public record ContentResourceDefinition(
        Kind kind, String name, String displayName, String texture, String minedWith, boolean dropsSelf)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The tools that have a {@code minecraft:mineable/<tool>} block tag. */
    public static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern TEXTURE = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_.-]+(/[a-z0-9_.-]+)*");

    /** What a definition generates files for. */
    public enum Kind {
        ITEM,
        BLOCK
    }

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException with {@code PLEXUS-RESOURCE-009} for a name or texture that Minecraft
     *     would reject, an unknown tool, or tool and drop settings on an item
     */
    public ContentResourceDefinition {
        if (kind == null || name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("PLEXUS-RESOURCE-009 '" + name
                    + "' is not a content name: use lowercase a-z, 0-9, _ . - (the name the mod's code registers)");
        }
        if (texture != null && !TEXTURE.matcher(texture).matches()) {
            throw new IllegalArgumentException("PLEXUS-RESOURCE-009 texture '" + texture + "' of " + name
                    + " is not a texture ID: write <namespace>:<path> in lowercase, such as minecraft:block/stone");
        }
        if (minedWith != null && !TOOLS.contains(minedWith)) {
            throw new IllegalArgumentException(
                    "PLEXUS-RESOURCE-009 minedWith '" + minedWith + "' of " + name + " must be one of " + TOOLS);
        }
        if (kind == Kind.ITEM && minedWith != null) {
            throw new IllegalArgumentException("PLEXUS-RESOURCE-009 minedWith is for blocks; " + name + " is an item");
        }
    }

    /** The texture ID this definition uses, with the mod's namespace filled in. */
    public String textureId(String modId) {
        if (texture == null) {
            return modId + ":" + (kind == Kind.BLOCK ? "block/" : "item/") + name;
        }
        return texture.contains(":") ? texture : modId + ":" + texture;
    }

    /** The English name: {@link #displayName()}, or {@code name} in title case ({@code ruby_block} is "Ruby Block"). */
    public String englishName() {
        if (displayName != null) {
            return displayName;
        }
        StringBuilder words = new StringBuilder();
        for (String word : name.split("[_.-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!words.isEmpty()) {
                words.append(' ');
            }
            words.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return words.toString();
    }
}
