package dev.plexusmodloader.installer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/** The Plexus Mod Loader logo (a wireframe cube, as on the website) drawn for the launcher profile and the window. */
final class ProfileIcon {

    private ProfileIcon() {}

    private static final Color BACKGROUND = new Color(0x1b, 0x1d, 0x22);
    private static final Color STROKE = new Color(0xf5, 0xb7, 0x00);

    /** Hidden edges, then visible edges, in the website logo's 64 x 64 coordinates. */
    private static final double[][] BACK_EDGES = {
        {24.2, 38.9, 8.9, 51.1}, {24.2, 38.9, 23.6, 7.1}, {24.2, 38.9, 55.6, 42.5},
    };

    private static final double[][] FRONT_EDGES = {
        {8.9, 51.1, 7.0, 11.8}, {8.9, 51.1, 47.5, 56.9}, {23.6, 7.1, 7.0, 11.8},
        {23.6, 7.1, 57.0, 8.5}, {7.0, 11.8, 48.6, 14.0}, {55.6, 42.5, 47.5, 56.9},
        {55.6, 42.5, 57.0, 8.5}, {47.5, 56.9, 48.6, 14.0}, {57.0, 8.5, 48.6, 14.0},
    };

    static BufferedImage image(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(BACKGROUND);
            g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.25, size * 0.25));
            // The logo fills 80% of the tile.
            double scale = size * 0.8 / 64.0;
            double offset = size * 0.1;
            draw(
                    g,
                    BACK_EDGES,
                    scale,
                    offset,
                    2.5,
                    new Color(STROKE.getRed(), STROKE.getGreen(), STROKE.getBlue(), 115));
            draw(g, FRONT_EDGES, scale, offset, 4.0, STROKE);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void draw(Graphics2D g, double[][] edges, double scale, double offset, double width, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) (width * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] edge : edges) {
            g.draw(new Line2D.Double(
                    offset + edge[0] * scale,
                    offset + edge[1] * scale,
                    offset + edge[2] * scale,
                    offset + edge[3] * scale));
        }
    }

    /** A {@code data:} URI of the 128 x 128 icon, the format {@code launcher_profiles.json} keeps icons in. */
    static String dataUri() {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(image(128), "png", png);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode the profile icon", e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
    }
}
