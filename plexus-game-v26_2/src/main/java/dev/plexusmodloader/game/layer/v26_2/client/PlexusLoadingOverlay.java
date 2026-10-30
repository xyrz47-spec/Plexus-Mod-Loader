package dev.plexusmodloader.game.layer.v26_2.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.plexusmodloader.game.layer.v26_2.LayerState;
import dev.plexusmodloader.game.version.layer.GameLayerContext;
import dev.plexusmodloader.game.version.layer.LoadedModCounts;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Poly Mod Loader's marks on the game's loading screen ({@code LoadingOverlay}), for every loader Poly Mod Loader runs.
 * The screen stays the game's own: its background (Mojang red, or black with the monochrome logo option), the Mojang
 * Studios logo, the progress bar and their fades. Poly Mod Loader adds, with the same fades, its spinning gold cube and
 * name at the bottom right (where NeoForge's early window, which Poly Mod Loader gives the same cube, has them) and the
 * mods each loader runs under the progress bar.
 *
 * <p>Called after {@code LoadingOverlay.extractRenderState} ({@code poly:client_loading_overlay}), also for a host
 * loader's overlay (MinecraftForge's {@code ForgeLoadingOverlay}, which draws the game's screen and keeps its fades).
 * Thread: the render thread.
 */
public final class PlexusLoadingOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final AtomicBoolean FAILED = new AtomicBoolean();

    /** The cube's frames: 24 of 32 x 32 pixels, a quarter turn ({@code poly_cube.png}, Poly Mod Loader's own art). */
    private static final int CUBE = 32;

    private static final int FRAME_MILLIS = 60;

    // The game's own fade timing (LoadingOverlay: FADE_OUT_TIME 1000, FADE_IN_TIME 500).
    private static final float FADE_OUT_MILLIS = 1000.0f;
    private static final float FADE_IN_MILLIS = 500.0f;

    private static final Field FADE_IN = field("fadeIn");
    private static final Field FADE_IN_START = field("fadeInStart");
    private static final Field FADE_OUT_START = field("fadeOutStart");

    private static volatile List<int[]> frames;

    private PlexusLoadingOverlay() {}

    /** After the game drew {@code overlay} into {@code graphics}: Poly Mod Loader's marks, faded as the screen. */
    public static void onExtractRenderStateReturn(
            LoadingOverlay overlay, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The game's window shows its loading screen: Poly Mod Loader's early window closes.
        LayerState.context().ifPresent(context -> context.gameWindow().shown());
        if (FAILED.get() || FADE_IN == null || FADE_IN_START == null || FADE_OUT_START == null) {
            return;
        }
        try {
            draw(overlay, graphics);
        } catch (RuntimeException | ReflectiveOperationException e) {
            // The game's screen is complete without the marks; draw them no more rather than fail every frame.
            if (FAILED.compareAndSet(false, true)) {
                LOGGER.warn("[Poly Mod Loader] Loading screen marks left out: {}", e.toString());
            }
        }
    }

    private static void draw(LoadingOverlay overlay, GuiGraphicsExtractor graphics)
            throws ReflectiveOperationException {
        long now = Util.getMillis();
        long fadeOutStart = FADE_OUT_START.getLong(overlay);
        long fadeInStart = FADE_IN_START.getLong(overlay);
        float fadeOut = fadeOutStart > -1L ? (now - fadeOutStart) / FADE_OUT_MILLIS : -1.0f;
        float fadeIn = fadeInStart > -1L ? (now - fadeInStart) / FADE_IN_MILLIS : -1.0f;
        // As the game fades its logo and its progress bar.
        float logoAlpha;
        if (fadeOut >= 1.0f) {
            logoAlpha = 1.0f - Mth.clamp(fadeOut - 1.0f, 0.0f, 1.0f);
        } else if (FADE_IN.getBoolean(overlay)) {
            logoAlpha = Mth.clamp(fadeIn, 0.0f, 1.0f);
        } else {
            logoAlpha = 1.0f;
        }
        float barAlpha = fadeOut < 1.0f ? 1.0f - Mth.clamp(fadeOut, 0.0f, 1.0f) : 0.0f;
        if (logoAlpha <= 0.0f && barAlpha <= 0.0f) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if (logoAlpha > 0.0f) {
            List<int[]> cube = frames();
            if (!cube.isEmpty()) {
                int[] frame = cube.get((int) ((now / FRAME_MILLIS) % cube.size()));
                drawFrame(graphics, frame, width - CUBE - 6, height - CUBE - 16, logoAlpha);
            }
            String name = "Plexus Mod Loader";
            pixelText(graphics, name, width - pixelWidth(name) - 6, height - 12, logoAlpha * 0.9f, 0xFFFFFF);
        }

        if (barAlpha > 0.0f) {
            LoadedModCounts counts =
                    LayerState.context().map(GameLayerContext::modCounts).orElse(LoadedModCounts.NONE);
            String line = countsLine(counts);
            // The game's progress bar: centred at height * 0.8325, 10 pixels high.
            int barCentre = (int) (height * 0.8325);
            if (!line.isEmpty()) {
                pixelText(graphics, line, (width - pixelWidth(line)) / 2, barCentre + 10, barAlpha * 0.85f, 0xFFFFFF);
            }
            if (counts.hostIncompatibleMods() > 0) {
                String left = counts.hostIncompatibleMods() + " not loaded: Plexus Mod Loader can't run them yet";
                pixelText(graphics, left, (width - pixelWidth(left)) / 2, barCentre + 21, barAlpha * 0.85f, 0xFFE08A);
            }
            if (REPORTED.compareAndSet(false, true)) {
                LOGGER.info("[Poly Mod Loader] Loading screen marks: {}", line.isEmpty() ? "no mods" : line);
            }
        }
    }

    /** {@code Mods: Fabric 34 · NeoForge 53 · Plexus 2}: the loaders whose mods run; empty with none. */
    static String countsLine(LoadedModCounts counts) {
        List<String> parts = new ArrayList<>();
        add(parts, "Fabric", counts.fabricMods());
        add(parts, "Quilt", counts.quiltMods());
        add(parts, "Forge", counts.forgeMods());
        add(parts, "NeoForge", counts.neoForgeMods());
        add(parts, "Plexus", counts.polyMods());
        return parts.isEmpty() ? "" : "Mods: " + String.join("  ·  ", parts);
    }

    private static void add(List<String> parts, String loader, int count) {
        if (count > 0) {
            parts.add(loader + " " + count);
        }
    }

    /** One frame, its runs of equal pixels as rectangles, each pixel's alpha times {@code alpha}. */
    private static void drawFrame(GuiGraphicsExtractor graphics, int[] frame, int x, int y, float alpha) {
        for (int row = 0; row < CUBE; row++) {
            int column = 0;
            while (column < CUBE) {
                int pixel = frame[row * CUBE + column];
                int end = column + 1;
                while (end < CUBE && frame[row * CUBE + end] == pixel) {
                    end++;
                }
                int pixelAlpha = pixel >>> 24;
                if (pixelAlpha != 0) {
                    int shown = Math.round(pixelAlpha * alpha);
                    if (shown > 0) {
                        graphics.fill(x + column, y + row, x + end, y + row + 1, (shown << 24) | (pixel & 0xFFFFFF));
                    }
                }
                column = end;
            }
        }
    }

    /**
     * {@code text} in Poly Mod Loader's 5 x 7 pixel letters (upper case; digits; space, colon, middle dot, apostrophe,
     * full stop, hyphen, slash), with a shadow one pixel down and right as the game's text has. The game's font is not
     * used: MinecraftForge's first loading screen comes before the game loads it.
     */
    private static void pixelText(GuiGraphicsExtractor graphics, String text, int x, int y, float alpha, int rgb) {
        int shadow = (rgb >> 2) & 0x3F3F3F;
        drawLetters(graphics, text, x + 1, y + 1, argb(alpha, shadow));
        drawLetters(graphics, text, x, y, argb(alpha, rgb));
    }

    private static void drawLetters(GuiGraphicsExtractor graphics, String text, int x, int y, int colour) {
        if ((colour >>> 24) == 0) {
            return;
        }
        int left = x;
        for (char letter : text.toUpperCase(Locale.ROOT).toCharArray()) {
            String[] rows = LETTERS.getOrDefault(letter, LETTERS.get(' '));
            for (int row = 0; row < rows.length; row++) {
                int column = 0;
                while (column < rows[row].length()) {
                    if (rows[row].charAt(column) != '1') {
                        column++;
                        continue;
                    }
                    int end = column + 1;
                    while (end < rows[row].length() && rows[row].charAt(end) == '1') {
                        end++;
                    }
                    graphics.fill(left + column, y + row, left + end, y + row + 1, colour);
                    column = end;
                }
            }
            left += LETTER_ADVANCE;
        }
    }

    /** The width of {@code text} in Poly Mod Loader's pixel letters. */
    static int pixelWidth(String text) {
        return text.isEmpty() ? 0 : text.length() * LETTER_ADVANCE - 1;
    }

    private static final int LETTER_ADVANCE = 6;

    private static final Map<Character, String[]> LETTERS = Map.ofEntries(
            letter('A', ".111.", "1...1", "1...1", "11111", "1...1", "1...1", "1...1"),
            letter('B', "1111.", "1...1", "1...1", "1111.", "1...1", "1...1", "1111."),
            letter('C', ".111.", "1...1", "1....", "1....", "1....", "1...1", ".111."),
            letter('D', "1111.", "1...1", "1...1", "1...1", "1...1", "1...1", "1111."),
            letter('E', "11111", "1....", "1....", "1111.", "1....", "1....", "11111"),
            letter('F', "11111", "1....", "1....", "1111.", "1....", "1....", "1...."),
            letter('G', ".111.", "1...1", "1....", "1.111", "1...1", "1...1", ".1111"),
            letter('H', "1...1", "1...1", "1...1", "11111", "1...1", "1...1", "1...1"),
            letter('I', ".111.", "..1..", "..1..", "..1..", "..1..", "..1..", ".111."),
            letter('J', "..111", "...1.", "...1.", "...1.", "1..1.", "1..1.", ".11.."),
            letter('K', "1...1", "1..1.", "1.1..", "11...", "1.1..", "1..1.", "1...1"),
            letter('L', "1....", "1....", "1....", "1....", "1....", "1....", "11111"),
            letter('M', "1...1", "11.11", "1.1.1", "1.1.1", "1...1", "1...1", "1...1"),
            letter('N', "1...1", "11..1", "1.1.1", "1..11", "1...1", "1...1", "1...1"),
            letter('O', ".111.", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."),
            letter('P', "1111.", "1...1", "1...1", "1111.", "1....", "1....", "1...."),
            letter('Q', ".111.", "1...1", "1...1", "1...1", "1.1.1", "1..1.", ".11.1"),
            letter('R', "1111.", "1...1", "1...1", "1111.", "1.1..", "1..1.", "1...1"),
            letter('S', ".1111", "1....", "1....", ".111.", "....1", "....1", "1111."),
            letter('T', "11111", "..1..", "..1..", "..1..", "..1..", "..1..", "..1.."),
            letter('U', "1...1", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."),
            letter('V', "1...1", "1...1", "1...1", "1...1", "1...1", ".1.1.", "..1.."),
            letter('W', "1...1", "1...1", "1...1", "1.1.1", "1.1.1", "11.11", "1...1"),
            letter('X', "1...1", "1...1", ".1.1.", "..1..", ".1.1.", "1...1", "1...1"),
            letter('Y', "1...1", "1...1", ".1.1.", "..1..", "..1..", "..1..", "..1.."),
            letter('Z', "11111", "....1", "...1.", "..1..", ".1...", "1....", "11111"),
            letter('0', ".111.", "1...1", "1..11", "1.1.1", "11..1", "1...1", ".111."),
            letter('1', "..1..", ".11..", "..1..", "..1..", "..1..", "..1..", ".111."),
            letter('2', ".111.", "1...1", "....1", "...1.", "..1..", ".1...", "11111"),
            letter('3', "11111", "...1.", "..1..", "...1.", "....1", "1...1", ".111."),
            letter('4', "...1.", "..11.", ".1.1.", "1..1.", "11111", "...1.", "...1."),
            letter('5', "11111", "1....", "1111.", "....1", "....1", "1...1", ".111."),
            letter('6', "..11.", ".1...", "1....", "1111.", "1...1", "1...1", ".111."),
            letter('7', "11111", "....1", "...1.", "..1..", ".1...", ".1...", ".1..."),
            letter('8', ".111.", "1...1", "1...1", ".111.", "1...1", "1...1", ".111."),
            letter('9', ".111.", "1...1", "1...1", ".1111", "....1", "...1.", ".11.."),
            letter(' ', ".....", ".....", ".....", ".....", ".....", ".....", "....."),
            letter(':', ".....", "..1..", ".....", ".....", ".....", "..1..", "....."),
            letter('\u00b7', ".....", ".....", ".....", "..1..", ".....", ".....", "....."),
            letter('\'', "..1..", "..1..", ".1...", ".....", ".....", ".....", "....."),
            letter('.', ".....", ".....", ".....", ".....", ".....", ".....", "..1.."),
            letter('-', ".....", ".....", ".....", ".111.", ".....", ".....", "....."),
            letter('/', "....1", "...1.", "...1.", "..1..", ".1...", ".1...", "1...."));

    private static Map.Entry<Character, String[]> letter(char letter, String... rows) {
        return Map.entry(letter, rows);
    }

    /** The cube's frames from {@code poly_cube.png} beside this class; none when it cannot be read. */
    private static List<int[]> frames() {
        List<int[]> loaded = frames;
        if (loaded != null) {
            return loaded;
        }
        List<int[]> read = new ArrayList<>();
        try (InputStream in = PlexusLoadingOverlay.class.getResourceAsStream("poly_cube.png")) {
            if (in == null) {
                LOGGER.warn("[Poly Mod Loader] poly_cube.png is missing; no cube on the loading screen");
            } else {
                try (NativeImage sheet = NativeImage.read(in)) {
                    if (sheet.getWidth() == CUBE && sheet.getHeight() % CUBE == 0) {
                        for (int top = 0; top < sheet.getHeight(); top += CUBE) {
                            int[] frame = new int[CUBE * CUBE];
                            for (int row = 0; row < CUBE; row++) {
                                for (int column = 0; column < CUBE; column++) {
                                    frame[row * CUBE + column] = sheet.getPixel(column, top + row);
                                }
                            }
                            read.add(frame);
                        }
                    } else {
                        LOGGER.warn(
                                "[Poly Mod Loader] poly_cube.png is not 32 pixels wide; no cube on the loading screen");
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[Poly Mod Loader] poly_cube.png could not be read: {}", e.toString());
        }
        loaded = List.copyOf(read);
        frames = loaded;
        return loaded;
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | rgb;
    }

    /** A field of the game's {@code LoadingOverlay}; null, and no marks, when this game version has none. */
    private static Field field(String name) {
        try {
            Field field = LoadingOverlay.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            LOGGER.warn("[Poly Mod Loader] LoadingOverlay has no field {}; no marks on the loading screen", name);
            return null;
        }
    }
}
