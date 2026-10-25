package dev.polymodloader.runtime.discovery;

/**
 * Which loader a jar was built for, decided by the metadata file it contains (design doc §11 step
 * 5, "Compatibility Classification").
 */
public enum ModFileKind {
    /** Contains {@code poly.mod.json}. */
    POLY_NATIVE("poly.mod.json"),
    /** Contains {@code fabric.mod.json}. */
    FABRIC("fabric.mod.json"),
    /**
     * Contains {@code quilt.mod.json}. After {@link #FABRIC}: a jar with both files is a Fabric mod and is {@code
     * alsoFor} Quilt (ADR-0027).
     */
    QUILT("quilt.mod.json"),
    /** Contains {@code META-INF/neoforge.mods.toml}. */
    NEOFORGE("META-INF/neoforge.mods.toml"),
    /** Contains {@code META-INF/mods.toml} (MinecraftForge). */
    FORGE("META-INF/mods.toml"),
    /**
     * No mod metadata, but its manifest names an {@code FMLModType} (a NeoForge or Forge library or language
     * provider, such as Kotlin for Forge).
     */
    FML_LIBRARY(""),
    /** The file could not be read as a jar (damaged or not a zip). */
    UNREADABLE(""),
    /** No known mod metadata. */
    UNKNOWN("");

    private final String metadataPath;

    ModFileKind(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    /** Path of the metadata file that identifies this kind inside a jar; empty for kinds found otherwise. */
    public String metadataPath() {
        return metadataPath;
    }
}
