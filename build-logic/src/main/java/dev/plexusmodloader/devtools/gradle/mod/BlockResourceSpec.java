package dev.plexusmodloader.devtools.gradle.mod;

/** Settings of one {@code polyResources { block("name") { ... } }} entry. */
public class BlockResourceSpec {

    private String displayName;
    private String texture;
    private String minedWith;
    private boolean dropsSelf = true;

    /** English name; unset, it is the name in title case ({@code ruby_block} is "Ruby Block"). */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Texture of all six faces; unset, it is {@code <mod>:block/<name>}, the file {@code textures/block/<name>.png}. */
    public String getTexture() {
        return texture;
    }

    public void setTexture(String texture) {
        this.texture = texture;
    }

    /** The tool that mines the block fast: {@code pickaxe}, {@code axe}, {@code shovel} or {@code hoe}; unset, none. */
    public String getMinedWith() {
        return minedWith;
    }

    public void setMinedWith(String minedWith) {
        this.minedWith = minedWith;
    }

    /** Whether breaking the block drops it (default {@code true}). */
    public boolean getDropsSelf() {
        return dropsSelf;
    }

    public void setDropsSelf(boolean dropsSelf) {
        this.dropsSelf = dropsSelf;
    }
}
