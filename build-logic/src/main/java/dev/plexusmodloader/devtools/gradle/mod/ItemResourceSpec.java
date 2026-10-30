package dev.plexusmodloader.devtools.gradle.mod;

/** Settings of one {@code polyResources { item("name") { ... } }} entry. */
public class ItemResourceSpec {

    private String displayName;
    private String texture;

    /** English name; unset, it is the name in title case ({@code ruby_gem} is "Ruby Gem"). */
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Texture ID; unset, it is {@code <mod>:item/<name>}, the file {@code textures/item/<name>.png}. */
    public String getTexture() {
        return texture;
    }

    public void setTexture(String texture) {
        this.texture = texture;
    }
}
