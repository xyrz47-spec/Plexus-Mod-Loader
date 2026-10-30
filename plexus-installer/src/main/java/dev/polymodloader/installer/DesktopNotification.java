package dev.polymodloader.installer;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import javax.swing.Timer;

/**
 * A system notification (a Windows toast) for the end of an install, shown from a short-lived tray icon. The
 * icon stays for {@link #SHOWN_MILLIS} so the notification can be read and clicked, then goes away.
 *
 * <p>Thread: call on the Swing event thread.
 */
final class DesktopNotification {

    static final int SHOWN_MILLIS = 10_000;

    private DesktopNotification() {}

    /**
     * Shows {@code message}; {@code onClick} runs when the player clicks it, {@code afterwards} once the
     * notification is gone (clicked or not).
     *
     * @return false when this desktop has no notification area, and nothing was shown
     */
    static boolean show(String title, String message, boolean error, Runnable onClick, Runnable afterwards) {
        if (!SystemTray.isSupported()) {
            return false;
        }
        SystemTray tray = SystemTray.getSystemTray();
        TrayIcon icon = new TrayIcon(ProfileIcon.image(64), title);
        icon.setImageAutoSize(true);
        try {
            tray.add(icon);
        } catch (AWTException e) {
            return false;
        }
        boolean[] finished = {false};
        Runnable finish = () -> {
            if (!finished[0]) {
                finished[0] = true;
                tray.remove(icon);
                afterwards.run();
            }
        };
        icon.addActionListener(event -> {
            onClick.run();
            finish.run();
        });
        icon.displayMessage(title, message, error ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO);
        Timer timer = new Timer(SHOWN_MILLIS, event -> finish.run());
        timer.setRepeats(false);
        timer.start();
        return true;
    }
}
