package dev.plexusmodloader.earlywindow;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Draws the game's loading screen as it looks before its first frame (its background, the Mojang Studios logo, the
 * progress bar) with Poly Mod Loader's marks where the game layers put them on the game's screen: the spinning gold
 * cube and the name at the bottom right, the mods each loader runs under the bar. What Poly Mod Loader is doing is at
 * the bottom left. The same painter draws the window and its snapshots.
 */
final class LoadingScreenPainter {

    // The game's LoadingOverlay: Mojang red (239, 50, 61), or black with the monochrome logo option.
    static final int MOJANG_RED = 0xEF323D;
    static final int BLACK = 0x000000;

    private static final int FRAME_MILLIS = 60;
    private static final int WHITE = 0xFFFFFF;

    private final BufferedImage logo;
    private final List<BufferedImage> cube;
    private final boolean darkBackground;

    /**
     * @param logo the game's {@code mojangstudios.png}; null draws no logo
     * @param cube the cube's frames, 32 x 32 each; empty draws no cube
     * @param darkBackground the game's monochrome logo option ({@code darkMojangStudiosBackground})
     */
    LoadingScreenPainter(BufferedImage logo, List<BufferedImage> cube, boolean darkBackground) {
        this.logo = logo;
        this.cube = List.copyOf(cube);
        this.darkBackground = darkBackground;
    }

    /** The screen's background colour, RGB. */
    int background() {
        return darkBackground ? BLACK : MOJANG_RED;
    }

    /** Paints {@code state} into {@code graphics}, a window of {@code width} x {@code height} pixels. */
    void paint(Graphics2D graphics, int width, int height, int guiScaleOption, LoadingScreenState state, long now) {
        LoadingScreenLayout layout = LoadingScreenLayout.of(width, height, guiScaleOption);
        graphics.setColor(new Color(background()));
        graphics.fillRect(0, 0, width, height);

        AffineTransform saved = graphics.getTransform();
        graphics.scale(layout.scale(), layout.scale());
        try {
            paintLogo(graphics, layout);
            paintBar(graphics, layout.progressBar(), state.progress());
            if (!cube.isEmpty()) {
                int[] at = layout.cube();
                BufferedImage frame = cube.get((int) ((now / FRAME_MILLIS) % cube.size()));
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(frame, at[0], at[1], null);
            }
            String name = state.name();
            int[] nameAt = layout.name(PixelLetters.width(name));
            text(graphics, name, nameAt[0], nameAt[1], 0.9f, WHITE);
            int line = 0;
            for (String under : state.underBar()) {
                int[] at = layout.underBar(line++, PixelLetters.width(under));
                text(graphics, under, at[0], at[1], 0.85f, WHITE);
            }
            String status = state.status();
            if (!status.isEmpty()) {
                text(graphics, status, 6, layout.guiHeight() - 12, 0.85f, WHITE);
            }
        } finally {
            graphics.setTransform(saved);
        }
    }

    private void paintLogo(Graphics2D graphics, LoadingScreenLayout layout) {
        if (logo == null) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int half = logo.getHeight() / 2;
        int[] left = layout.logoLeft();
        int[] right = layout.logoRight();
        // The game draws the texture's top half (MOJANG) on the left and its bottom half (STUDIOS) on the right.
        graphics.drawImage(
                logo, left[0], left[1], left[0] + left[2], left[1] + left[3], 0, 0, logo.getWidth(), half, null);
        graphics.drawImage(
                logo,
                right[0],
                right[1],
                right[0] + right[2],
                right[1] + right[3],
                0,
                half,
                logo.getWidth(),
                logo.getHeight(),
                null);
    }

    // The game's extractProgressBar: a one pixel frame and the filled part two pixels inside it.
    private static void paintBar(Graphics2D graphics, int[] bar, float progress) {
        int minX = bar[0];
        int minY = bar[1];
        int maxX = bar[2];
        int maxY = bar[3];
        int filled = (int) Math.ceil((maxX - minX - 2) * clamp(progress));
        graphics.setColor(new Color(WHITE));
        fill(graphics, minX + 2, minY + 2, minX + filled, maxY - 2);
        fill(graphics, minX + 1, minY, maxX - 1, minY + 1);
        fill(graphics, minX + 1, maxY, maxX - 1, maxY - 1);
        fill(graphics, minX, minY, minX + 1, maxY);
        fill(graphics, maxX, minY, maxX - 1, maxY);
    }

    // As GuiGraphics.fill: either corner first.
    private static void fill(Graphics2D graphics, int x1, int y1, int x2, int y2) {
        int x = Math.min(x1, x2);
        int y = Math.min(y1, y2);
        int w = Math.abs(x2 - x1);
        int h = Math.abs(y2 - y1);
        if (w > 0 && h > 0) {
            graphics.fillRect(x, y, w, h);
        }
    }

    /** Text with the game's shadow, one pixel down and right, a quarter as bright. */
    private static void text(Graphics2D graphics, String text, int x, int y, float alpha, int rgb) {
        int shadow = (rgb >> 2) & 0x3F3F3F;
        letters(graphics, text, x + 1, y + 1, alpha, shadow);
        letters(graphics, text, x, y, alpha, rgb);
    }

    private static void letters(Graphics2D graphics, String text, int x, int y, float alpha, int rgb) {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(alpha)));
        graphics.setColor(new Color(rgb));
        int left = x;
        for (char letter : PixelLetters.shown(text).toCharArray()) {
            String[] rows = PixelLetters.rows(letter);
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
                    graphics.fillRect(left + column, y + row, end - column, 1);
                    column = end;
                }
            }
            left += PixelLetters.ADVANCE;
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
