package dev.plexusmodloader.earlywindow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Poly Mod Loader's early loading window, seen from the game's process: it starts the window's own process (see
 * {@link EarlyWindowMain}) and tells it what to show. The window looks like the game's loading screen with Poly Mod
 * Loader's marks, so that it carries on into the game's screen when the game's window opens over it.
 *
 * <p>Every method may be called from any thread. Nothing here fails the launch: when the process cannot start or
 * stops, the methods do nothing and {@code log} gets one line saying why.
 */
public final class EarlyWindow implements AutoCloseable {

    private static final String MAIN_CLASS = EarlyWindowMain.class.getName();

    private final Process process;
    private final Writer input;
    private final Consumer<String> log;
    private boolean closed;

    private EarlyWindow(Process process, Consumer<String> log) {
        this.process = process;
        this.input = process == null ? null : new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        this.log = log;
        this.closed = process == null;
    }

    /** A window that shows nothing, for launches without one. */
    public static EarlyWindow none() {
        return new EarlyWindow(null, line -> {});
    }

    /**
     * Starts the window's process with the window hidden until {@link #show()}.
     *
     * @param log receives the window process's own output and one line when the window cannot be shown
     */
    public static EarlyWindow start(EarlyWindowSettings settings, Consumer<String> log) {
        Optional<Path> java = javaCommand();
        Optional<String> classPath = classPath();
        if (java.isEmpty() || classPath.isEmpty()) {
            log.accept("Early loading window not shown: Java executable or classpath not found");
            return none();
        }
        List<String> command = new ArrayList<>(List.of(
                java.get().toString(),
                "-Xms8m",
                "-Xmx64m",
                "-XX:+UseSerialGC",
                "-XX:TieredStopAtLevel=1",
                "-Djava.awt.headless=false",
                "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8"));
        if (!isMac()) {
            // The game's window is sized in screen pixels on Windows and Linux; so is this one.
            command.add("-Dsun.java2d.uiScale=1");
        }
        command.addAll(List.of("-cp", classPath.get(), MAIN_CLASS));
        command.addAll(settings.toArguments());
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            forwardOutput(process, log);
            EarlyWindow window = new EarlyWindow(process, log);
            Runtime.getRuntime().addShutdownHook(new Thread(window::destroy, "Poly early window shutdown"));
            return window;
        } catch (IOException | RuntimeException e) {
            log.accept("Failed to start early loading window process " + "(" + e + ")");
            return none();
        }
    }

    /** Whether the window's process runs. */
    public synchronized boolean isRunning() {
        return !closed && process.isAlive();
    }

    /** Opens the window. */
    public void show() {
        send(EarlyWindowProtocol.show());
    }

    /** What Poly Mod Loader is doing, shown at the bottom left. */
    public void status(String text) {
        send(EarlyWindowProtocol.status(text));
    }

    /** The progress bar's filled share, {@code 0} to {@code 1}; it never goes back. */
    public void progress(float value) {
        send(EarlyWindowProtocol.progress(value));
    }

    /** The lines under the progress bar. */
    public void underBar(List<String> lines) {
        send(EarlyWindowProtocol.underBar(lines));
    }

    /** Writes what the window shows to {@code file}, a PNG image (for checks). */
    public void snapshot(Path file) {
        send(EarlyWindowProtocol.snapshot(file.toAbsolutePath().toString()));
    }

    /** Closes the window and ends its process. Further calls do nothing. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        send(EarlyWindowProtocol.close());
        closed = true;
        try {
            input.close();
        } catch (IOException e) {
            // The process has gone already; it closes its window when it ends.
        }
    }

    private synchronized void send(String line) {
        if (closed) {
            return;
        }
        try {
            input.write(line);
            input.write('\n');
            input.flush();
        } catch (IOException e) {
            closed = true;
            log.accept("Early loading window stopped and will show nothing more " + "(" + e + ")");
        }
    }

    private void destroy() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    private static void forwardOutput(Process process, Consumer<String> log) {
        Thread reader = new Thread(
                () -> {
                    try (BufferedReader output = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = output.readLine()) != null) {
                            log.accept("Early window: " + line);
                        }
                    } catch (IOException e) {
                        // The process ended; its output with it.
                    }
                },
                "Poly early window output");
        reader.setDaemon(true);
        reader.start();
    }

    /** The Java that runs this process; on Windows its {@code javaw.exe}, which opens no console window. */
    static Optional<Path> javaCommand() {
        Optional<Path> running =
                ProcessHandle.current().info().command().map(Path::of).filter(Files::isRegularFile);
        Path bin = running.map(Path::getParent).orElse(Path.of(System.getProperty("java.home"), "bin"));
        if (isWindows()) {
            Path javaw = bin.resolve("javaw.exe");
            if (Files.isRegularFile(javaw)) {
                return Optional.of(javaw);
            }
        }
        Path java = bin.resolve(isWindows() ? "java.exe" : "java");
        return Files.isRegularFile(java) ? Optional.of(java) : running;
    }

    /**
     * Where the window's classes and images are: this module's jar, or, in a development run, its class and
     * resource folders.
     */
    static Optional<String> classPath() {
        Set<Path> roots = new LinkedHashSet<>();
        root(EarlyWindowMain.class.getResource("EarlyWindowMain.class"), "EarlyWindowMain.class")
                .ifPresent(roots::add);
        root(EarlyWindowMain.class.getResource("poly_cube.png"), "poly_cube.png")
                .ifPresent(roots::add);
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join(
                java.io.File.pathSeparator, roots.stream().map(Path::toString).toList()));
    }

    // The folder or jar a resource of this package comes from.
    private static Optional<Path> root(URL url, String name) {
        if (url == null) {
            return Optional.empty();
        }
        try {
            String text = url.toString();
            if (text.startsWith("jar:file:")) {
                int bang = text.indexOf("!/");
                return Optional.of(Path.of(URI.create(text.substring(4, bang))));
            }
            if (text.startsWith("file:")) {
                Path file = Path.of(url.toURI());
                int depth = EarlyWindowMain.class.getPackageName().split("\\.").length;
                Path root = file.getParent();
                for (int i = 0; i < depth; i++) {
                    root = root.getParent();
                }
                return Optional.of(root);
            }
        } catch (URISyntaxException | RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }
}
