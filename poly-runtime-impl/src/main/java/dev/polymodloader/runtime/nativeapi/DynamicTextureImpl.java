package dev.polymodloader.runtime.nativeapi;

import dev.polymodloader.api.client.DynamicTexture;
import dev.polymodloader.api.resource.ResourceId;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A dynamic texture's pixels, kept here so mods can draw before the game's texture exists; the game
 * layer binds the code that copies them to the graphics card.
 */
final class DynamicTextureImpl implements DynamicTexture {

    private final ResourceId id;
    private final int width;
    private final int height;
    private final int[] pixels;
    private volatile Consumer<int[]> uploader;

    DynamicTextureImpl(ResourceId id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    void bind(Consumer<int[]> upload) {
        this.uploader = Objects.requireNonNull(upload, "upload");
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void setPixel(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("(" + x + ", " + y + ") is outside " + width + "x" + height);
        }
        pixels[y * width + x] = argb;
    }

    @Override
    public void fill(int argb) {
        Arrays.fill(pixels, argb);
    }

    @Override
    public void upload() {
        Consumer<int[]> current = uploader;
        if (current == null) {
            throw new IllegalStateException("POLY-CLIENT-005 the game's textures are not ready yet");
        }
        current.accept(pixels);
    }
}
