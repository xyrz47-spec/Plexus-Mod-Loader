package dev.polymodloader.runtime;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.earlywindow.EarlyWindow;
import dev.polymodloader.earlywindow.EarlyWindowSettings;
import dev.polymodloader.game.version.GameVersionAdapter;
import dev.polymodloader.game.version.layer.GameWindowListener;
import dev.polymodloader.game.version.layer.LoadedModCounts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Poly Mod Loader's early loading window over a launch, with every loader: it opens as Poly Mod Loader starts, shows
 * each step of the launch, and closes when the game's window shows its loading screen, which looks the same ({@link
 * GameWindowListener}). A host loader (MinecraftForge, NeoForge) then opens no early window of its own.
 *
 * <p>The window opens only on the client, for a version adapter that names the game's logo and whose game layer tells
 * when the game's window shows ({@link GameVersionAdapter#loadingScreenLogo()}), not on macOS (not checked there), and
 * not with {@code -Dpoly.earlyWindow=off}. When the game's window never says it shows, the window closes on its own: 20
 * seconds after the game names its window, at the latest 3 minutes after the game starts.
 *
 * <p>Thread: the launch thread, and the game's render thread for {@link GameWindowListener}.
 */
final class StartupWindow implements GameWindowListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("Poly Mod Loader");

    /** The game's first frame of its loading screen is on screen by then. */
    private static final long CLOSE_AFTER_SHOWN_MILLIS = 300;

    private static final long CLOSE_AFTER_OPENING_MILLIS = 20_000;
    private static final long CLOSE_AFTER_START_MILLIS = 180_000;

    private final EarlyWindow window;
    private final Optional<Path> snapshotDirectory;
    private ScheduledExecutorService timer;

    private StartupWindow(EarlyWindow window, Optional<Path> snapshotDirectory) {
        this.window = window;
        this.snapshotDirectory = snapshotDirectory;
    }

    /** A launch without an early window. */
    static StartupWindow none() {
        return new StartupWindow(EarlyWindow.none(), Optional.empty());
    }

    /** Starts the window's process, hidden until {@link #noHost()}; or a launch without one. */
    static StartupWindow start(
            LaunchConfiguration configuration, GameVersionAdapter adapter, String gameVersion, String[] gameArguments) {
        Optional<String> logo = adapter.loadingScreenLogo();
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
        if (configuration.side() != GameSide.CLIENT
                || logo.isEmpty()
                || mac
                || System.getProperty("poly.earlyWindow", "on").equals("off")) {
            return none();
        }
        EarlyWindowSettings settings = EarlyWindowSettings.forGame(
                "Minecraft* Poly " + gameVersion, List.of(gameArguments), Optional.of(configuration.gameJar()), logo);
        EarlyWindow window = EarlyWindow.start(settings, line -> LOGGER.info("[Poly Mod Loader] {}", line));
        Optional<Path> snapshots = Optional.ofNullable(System.getProperty("poly.earlyWindow.snapshots"))
                .map(Path::of);
        StartupWindow startup = new StartupWindow(window, snapshots);
        startup.open();
        startup.step(0.02f, "Scanning for mods");
        return startup;
    }

    /** Opens the window; the host loader, if any, opens none ({@code GameHost.Request.polyEarlyWindow}). */
    private void open() {
        if (window.isRunning()) {
            window.show();
            LOGGER.info("[Poly Mod Loader] Early loading window opened");
        }
    }

    /** Whether the window shows: the host loader then opens no early window of its own. */
    boolean shows() {
        return window.isRunning();
    }

    /** A host loader ({@code Forge} or {@code NeoForge}) starts: its FML finds its mods and prepares the game. */
    void hostStarting(String loader) {
        step(0.2f, String.format(Locale.ROOT, "Starting %s", loader));
    }

    void discovered(int modFiles) {
        step(0.15f, String.format(Locale.ROOT, "Found %d mod files", modFiles));
    }

    void resolving() {
        step(0.3f, "Checking mods and dependencies");
    }

    /** The mods each loader runs, under the bar, as the game's loading screen shows them next. */
    void modsLoaded(LoadedModCounts counts) {
        String line = modsLine(counts);
        window.underBar(line.isEmpty() ? List.of() : List.of(line));
        window.progress(0.45f);
    }

    void preparing() {
        step(0.6f, "Preparing game for mods");
    }

    void initialisingMods(int mods) {
        step(0.8f, String.format(Locale.ROOT, "Starting %d mods", mods));
    }

    /** The game's {@code main} starts; the window closes when the game's own window shows. */
    void startingGame(String gameVersion) {
        step(0.9f, String.format(Locale.ROOT, "Starting Minecraft %s", gameVersion));
        snapshotDirectory.ifPresent(directory -> window.snapshot(directory.resolve("early-window.png")));
        closeIn(CLOSE_AFTER_START_MILLIS);
    }

    /** The game's {@code main} returned, or the launch stopped. */
    void gameEnded() {
        close();
    }

    @Override
    public void opening() {
        closeIn(CLOSE_AFTER_OPENING_MILLIS);
    }

    @Override
    public void shown() {
        closeIn(CLOSE_AFTER_SHOWN_MILLIS);
    }

    /** {@code Mods: Fabric 34  ·  Poly 2}, as the game layers' {@code PolyLoadingOverlay} writes it; empty with none. */
    static String modsLine(LoadedModCounts counts) {
        List<String> parts = new ArrayList<>();
        add(parts, "Fabric", counts.fabricMods());
        add(parts, "Quilt", counts.quiltMods());
        add(parts, "Forge", counts.forgeMods());
        add(parts, "NeoForge", counts.neoForgeMods());
        add(parts, "Poly", counts.polyMods());
        return parts.isEmpty() ? "" : "Mods: " + String.join("  ·  ", parts);
    }

    private static void add(List<String> parts, String loader, int count) {
        if (count > 0) {
            parts.add(loader + " " + count);
        }
    }

    private void step(float progress, String status) {
        window.status(status);
        window.progress(progress);
    }

    private synchronized void closeIn(long millis) {
        if (!window.isRunning()) {
            return;
        }
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "Poly early window close");
                thread.setDaemon(true);
                return thread;
            });
        }
        timer.schedule(this::close, millis, TimeUnit.MILLISECONDS);
    }

    private synchronized void close() {
        if (window.isRunning()) {
            window.close();
            LOGGER.info("[Poly Mod Loader] Early loading window closed");
        }
        if (timer != null) {
            timer.shutdown();
        }
    }
}
