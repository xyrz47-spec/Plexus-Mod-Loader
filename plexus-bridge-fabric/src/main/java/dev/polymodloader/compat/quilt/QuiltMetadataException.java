package dev.polymodloader.compat.quilt;

/**
 * A {@code quilt.mod.json} value Quilt Loader refuses; the Quilt bridge reports it as {@code PLEXUS-COMPAT-300}. The
 * message says which value and why, in Quilt Loader's words where it has them.
 */
public final class QuiltMetadataException extends Exception {

    private static final long serialVersionUID = 1L;

    public QuiltMetadataException(String message) {
        super(message);
    }

    public QuiltMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
