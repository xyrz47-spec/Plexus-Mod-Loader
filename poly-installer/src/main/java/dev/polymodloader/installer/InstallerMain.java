package dev.polymodloader.installer;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Main class of {@code PolyModLoader-<version>.jar} (and of the Windows {@code .exe}, which runs this jar).
 * Without arguments it opens the installer window; with arguments it is a command line tool.
 */
public final class InstallerMain {

    private InstallerMain() {}

    public static void main(String[] args) {
        InstallerPayload payload;
        try {
            payload = InstallerPayload.bundled();
        } catch (IOException | RuntimeException e) {
            fail(
                    "This Poly Mod Loader installer is damaged: " + e.getMessage() + ". Download it again.",
                    args.length == 0);
            return;
        }
        if (args.length == 0 && !GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> InstallerWindow.open(payload));
            return;
        }
        System.exit(new InstallerCommandLine(payload, System.out, System.err).run(List.of(args)));
    }

    private static void fail(String message, boolean window) {
        if (window && !GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "Poly Mod Loader", JOptionPane.ERROR_MESSAGE);
        } else {
            System.err.println(message);
        }
        System.exit(1);
    }
}
