package net.minecraftforge.internal;

import java.util.List;
import java.util.function.ObjIntConsumer;

/**
 * Test stand-in for MinecraftForge's {@code BrandingControl}, with the member {@code ForgeTitleBranding} calls (same
 * name and descriptor as Forge 26.1-62.0.9 to 26.2-65.1.5, checked with {@code javap}): each branding line with its
 * index counted up from the bottom, as Forge's {@code forEachLine(true, true, ...)} does for its title screen.
 */
public final class BrandingControl {

    /** Forge's two lines with {@code (includeMinecraft, reverse) = (true, true)}: the Minecraft line is at the bottom. */
    public static final List<String> LINES = List.of("Minecraft 26.2", "Forge 65.1.5 (5 mods loaded)");

    private BrandingControl() {}

    public static void forEachLine(boolean includeMinecraft, boolean reverse, ObjIntConsumer<String> line) {
        for (int i = 0; i < LINES.size(); i++) {
            line.accept(LINES.get(i), i);
        }
    }
}
