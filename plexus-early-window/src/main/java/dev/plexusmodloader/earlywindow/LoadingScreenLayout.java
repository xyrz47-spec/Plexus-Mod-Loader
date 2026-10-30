package dev.plexusmodloader.earlywindow;

/**
 * Where the game's loading screen puts its pieces, in GUI pixels, for a window of {@code framebufferWidth} x
 * {@code framebufferHeight} pixels. The numbers are the game's own (its {@code LoadingOverlay} and {@code Window} in
 * Minecraft 26.1, 26.2 and 26.3, read with {@code javap}), and the Poly Mod Loader marks sit where the game layers'
 * {@code PolyLoadingOverlay} draws them, so the early window and the game's screen that follows line up.
 *
 * @param scale GUI pixels to window pixels
 * @param guiWidth width in GUI pixels
 * @param guiHeight height in GUI pixels
 */
record LoadingScreenLayout(int scale, int guiWidth, int guiHeight) {

    /** The cube's frames are 32 x 32 GUI pixels. */
    static final int CUBE = 32;

    /** The layout of a window this size with the game's GUI scale option ({@code 0}: automatic). */
    static LoadingScreenLayout of(int framebufferWidth, int framebufferHeight, int guiScaleOption) {
        int scale = scale(framebufferWidth, framebufferHeight, guiScaleOption);
        return new LoadingScreenLayout(scale, scaled(framebufferWidth, scale), scaled(framebufferHeight, scale));
    }

    /** The game's {@code Window.calculateScale} (without forced Unicode). */
    static int scale(int framebufferWidth, int framebufferHeight, int guiScaleOption) {
        int scale = 1;
        while (scale != guiScaleOption
                && scale < framebufferWidth
                && scale < framebufferHeight
                && framebufferWidth / (scale + 1) >= 320
                && framebufferHeight / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    // Window.setGuiScale: the size divided by the scale, rounded up.
    private static int scaled(int framebuffer, int scale) {
        int whole = framebuffer / scale;
        return (double) framebuffer / scale > whole ? whole + 1 : whole;
    }

    private int centreX() {
        return (int) (guiWidth * 0.5);
    }

    private int centreY() {
        return (int) (guiHeight * 0.5);
    }

    private double logoHeight() {
        return Math.min(guiWidth * 0.75, guiHeight) * 0.25;
    }

    private int halfLogoHeight() {
        return (int) (logoHeight() * 0.5);
    }

    private int halfLogoWidth() {
        return (int) (logoHeight() * 4.0 * 0.5);
    }

    /** The left half of the Mojang Studios logo (the texture's top half): x, y, width, height. */
    int[] logoLeft() {
        return new int[] {centreX() - halfLogoWidth(), centreY() - halfLogoHeight(), halfLogoWidth(), (int) logoHeight()
        };
    }

    /** The right half of the logo (the texture's bottom half). */
    int[] logoRight() {
        return new int[] {centreX(), centreY() - halfLogoHeight(), halfLogoWidth(), (int) logoHeight()};
    }

    /** The progress bar's outer edges: min x, min y, max x, max y (the game's {@code extractProgressBar}). */
    int[] progressBar() {
        int barCentre = barCentre();
        return new int[] {guiWidth / 2 - halfLogoWidth(), barCentre - 5, guiWidth / 2 + halfLogoWidth(), barCentre + 5};
    }

    /** The bar's vertical centre. */
    int barCentre() {
        return (int) (guiHeight * 0.8325);
    }

    /** The cube's top left corner. */
    int[] cube() {
        return new int[] {guiWidth - CUBE - 6, guiHeight - CUBE - 16};
    }

    /** The top left corner of Poly Mod Loader's name, {@code nameWidth} GUI pixels wide. */
    int[] name(int nameWidth) {
        return new int[] {guiWidth - nameWidth - 6, guiHeight - 12};
    }

    /** The top left corner of line {@code line} (0, 1, ...) under the bar, {@code textWidth} GUI pixels wide. */
    int[] underBar(int line, int textWidth) {
        return new int[] {(guiWidth - textWidth) / 2, barCentre() + 10 + 11 * line};
    }
}
