package dev.polymodloader.game.layer.v26_3.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.polymodloader.game.layer.v26_3.Views;
import dev.polymodloader.game.version.layer.NativeDeclarations.DynamicTextureDeclaration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** The game textures behind mods' dynamic textures, created on first upload. */
public final class DynamicTextures {

    private static final Map<Identifier, DynamicTexture> TEXTURES = new ConcurrentHashMap<>();

    private DynamicTextures() {}

    /** Copies {@code pixels} (rows, {@code 0xAARRGGBB}) into the texture; on the render thread. */
    public static void upload(DynamicTextureDeclaration declaration, int[] pixels) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("POLY-CLIENT-005 dynamic textures are uploaded on the client thread");
        }
        Identifier id = Views.id(declaration.id());
        DynamicTexture texture = TEXTURES.computeIfAbsent(id, key -> {
            DynamicTexture created = new DynamicTexture(key::toString, declaration.width(), declaration.height(), true);
            minecraft.getTextureManager().register(key, created);
            return created;
        });
        NativeImage image = texture.getPixels();
        for (int y = 0; y < declaration.height(); y++) {
            for (int x = 0; x < declaration.width(); x++) {
                image.setPixel(x, y, pixels[y * declaration.width() + x]);
            }
        }
        texture.upload();
    }
}
