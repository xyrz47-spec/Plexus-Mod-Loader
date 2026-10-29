package dev.polymodloader.earlywindow;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * The early window's process: a window like the game's loading screen, opened where the game's window opens, until the
 * game's process says {@code close} or its output ends (the game's process has gone). See {@link EarlyWindowProtocol}.
 *
 * <p>The window never takes the keyboard focus, so that the game's window, which opens over it, gets it. It runs in a
 * process of its own so that it shares nothing with the game: not its window library (GLFW in 26.1 and 26.2, SDL in
 * 26.3), not its {@code java.awt.headless} setting.
 */
public final class EarlyWindowMain {

    private static final int FRAME_MILLIS = 33;

    /** The longest the window stays open; the game's process normally closes it within a minute or two. */
    private static final long MAX_LIFETIME_MILLIS = 15 * 60 * 1000L;

    private EarlyWindowMain() {}

    public static void main(String[] args) throws Exception {
        EarlyWindowSettings settings = EarlyWindowSettings.fromArguments(List.of(args));
        LoadingScreenPainter painter = new LoadingScreenPainter(
                EarlyWindowArt.logo(settings.gameJar(), settings.logoEntry()).orElse(null),
                EarlyWindowArt.cube(),
                settings.darkBackground());
        AtomicReference<LoadingScreenState> state = new AtomicReference<>(LoadingScreenState.initial());
        AtomicReference<JFrame> window = new AtomicReference<>();
        long started = System.currentTimeMillis();

        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            EarlyWindowProtocol.Command command =
                    EarlyWindowProtocol.parse(line).orElse(null);
            if (command == null) {
                continue;
            }
            switch (command) {
                case EarlyWindowProtocol.Show show ->
                    SwingUtilities.invokeAndWait(() -> {
                        if (window.get() == null && !GraphicsEnvironment.isHeadless()) {
                            window.set(open(settings, painter, state, started));
                        }
                    });
                case EarlyWindowProtocol.Status status -> state.updateAndGet(now -> now.withStatus(status.text()));
                case EarlyWindowProtocol.Progress progress ->
                    state.updateAndGet(now -> now.withProgress(progress.value()));
                case EarlyWindowProtocol.UnderBar under -> state.updateAndGet(now -> now.withUnderBar(under.lines()));
                case EarlyWindowProtocol.Snapshot snapshot -> snapshot(settings, painter, state.get(), snapshot.file());
                case EarlyWindowProtocol.Close close -> {
                    exit(window);
                    return;
                }
            }
        }
        exit(window);
    }

    private static JFrame open(
            EarlyWindowSettings settings,
            LoadingScreenPainter painter,
            AtomicReference<LoadingScreenState> state,
            long started) {
        JFrame frame = new JFrame(settings.title());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setFocusableWindowState(false);
        frame.setAutoRequestFocus(false);
        EarlyWindowArt.icon().ifPresent(frame::setIconImage);
        JComponent view = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                painter.paint(
                        (Graphics2D) graphics,
                        getWidth(),
                        getHeight(),
                        settings.guiScale(),
                        state.get(),
                        System.currentTimeMillis());
            }
        };
        view.setPreferredSize(new java.awt.Dimension(settings.width(), settings.height()));
        view.setOpaque(true);
        frame.setContentPane(view);
        frame.setBackground(new java.awt.Color(painter.background()));
        frame.pack();
        // The game centres its window's content on the primary monitor.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
        Insets insets = frame.getInsets();
        frame.setLocation(
                screen.x + (screen.width - settings.width()) / 2 - insets.left,
                screen.y + (screen.height - settings.height()) / 2 - insets.top);
        frame.setVisible(true);
        new Timer(FRAME_MILLIS, event -> {
                    if (System.currentTimeMillis() - started > MAX_LIFETIME_MILLIS) {
                        System.exit(0);
                    }
                    view.repaint();
                })
                .start();
        return frame;
    }

    private static void snapshot(
            EarlyWindowSettings settings, LoadingScreenPainter painter, LoadingScreenState state, String file) {
        BufferedImage image = new BufferedImage(settings.width(), settings.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            painter.paint(
                    graphics,
                    settings.width(),
                    settings.height(),
                    settings.guiScale(),
                    state,
                    System.currentTimeMillis());
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", Path.of(file).toFile());
        } catch (IOException | RuntimeException e) {
            System.err.println("Snapshot could not be written to " + file + " (" + e + ")");
        }
    }

    private static void exit(AtomicReference<JFrame> window) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = window.get();
                if (frame != null) {
                    frame.dispose();
                }
            });
        } catch (InterruptedException | java.lang.reflect.InvocationTargetException e) {
            // Ending the process closes the window too.
        }
        System.exit(0);
    }
}
