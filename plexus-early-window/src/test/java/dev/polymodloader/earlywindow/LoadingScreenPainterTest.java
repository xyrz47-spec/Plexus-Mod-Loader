package dev.polymodloader.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoadingScreenPainterTest {

    private static final int WIDTH = 854;
    private static final int HEIGHT = 480;

    @Test
    void artIsInTheModule() {
        List<BufferedImage> cube = EarlyWindowArt.cube();
        assertEquals(24, cube.size());
        assertEquals(32, cube.getFirst().getWidth());
        assertTrue(EarlyWindowArt.icon().isPresent());
        assertTrue(EarlyWindowArt.logo(Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test
    void drawsTheGamesBackgroundBarAndThePolyMarks() {
        BufferedImage image = paint(false, 0.5f);
        // Background: Mojang red.
        assertEquals(LoadingScreenPainter.MOJANG_RED, rgb(image, 4, 4));
        // The bar (93..333 x 194..204 GUI pixels at scale 2): its top edge is white, half of its inside filled.
        assertEquals(0xFFFFFF, rgb(image, 2 * 150, 2 * 194));
        assertEquals(0xFFFFFF, rgb(image, 2 * 100, 2 * 199));
        assertEquals(LoadingScreenPainter.MOJANG_RED, rgb(image, 2 * 300, 2 * 199));
        // The cube (389, 192 .. 421, 224): some of it is gold, not background.
        boolean gold = false;
        for (int y = 2 * 192; y < 2 * 224 && !gold; y++) {
            for (int x = 2 * 389; x < 2 * 421 && !gold; x++) {
                int pixel = rgb(image, x, y);
                gold = ((pixel >> 16) & 0xFF) > 200 && ((pixel >> 8) & 0xFF) > 120 && (pixel & 0xFF) < 100;
            }
        }
        assertTrue(gold, "no gold pixel where the cube is");
        // The name's first letter P starts at (332, 228): its top left pixel is lit.
        assertNotEquals(LoadingScreenPainter.MOJANG_RED, rgb(image, 2 * 332, 2 * 228));
    }

    @Test
    void monochromeOptionDrawsBlack() {
        assertEquals(LoadingScreenPainter.BLACK, rgb(paint(true, 0.0f), 4, 4));
    }

    @Test
    void emptyBarHasOnlyItsFrame() {
        BufferedImage image = paint(false, 0.0f);
        assertEquals(LoadingScreenPainter.MOJANG_RED, rgb(image, 2 * 100, 2 * 199));
        assertEquals(0xFFFFFF, rgb(image, 2 * 93, 2 * 199));
    }

    @Test
    void classPathNamesThisModule() {
        Optional<String> classPath = EarlyWindow.classPath();
        assertTrue(classPath.isPresent());
        assertFalse(classPath.get().isBlank());
        assertTrue(EarlyWindow.javaCommand().isPresent());
    }

    private static BufferedImage paint(boolean dark, float progress) {
        LoadingScreenPainter painter = new LoadingScreenPainter(null, EarlyWindowArt.cube(), dark);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            LoadingScreenState state = LoadingScreenState.initial()
                    .withProgress(progress)
                    .withStatus("Loading")
                    .withUnderBar(List.of("Mods: Fabric 2"));
            painter.paint(graphics, WIDTH, HEIGHT, 0, state, 0L);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }
}
