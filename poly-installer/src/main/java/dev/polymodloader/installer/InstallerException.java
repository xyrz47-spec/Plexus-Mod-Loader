package dev.polymodloader.installer;

/** An installation that cannot go on; the message tells the user what to do. */
final class InstallerException extends Exception {

    private static final long serialVersionUID = 1L;

    InstallerException(String message) {
        super(message);
    }

    InstallerException(String message, Throwable cause) {
        super(message, cause);
    }
}
