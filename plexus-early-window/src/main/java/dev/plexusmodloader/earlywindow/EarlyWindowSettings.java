package dev.plexusmodloader.earlywindow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How the early window looks: the game window's title, its size, the game's GUI scale and background options, and
 * where the game's Mojang Studios logo is.
 *
 * @param title the game window's title ({@code Minecraft* Poly <version>})
 * @param width the game window's width in pixels
 * @param height the game window's height in pixels
 * @param guiScale the game's GUI scale option ({@code 0}: automatic)
 * @param darkBackground the game's monochrome logo option ({@code darkMojangStudiosBackground})
 * @param gameJar the game's client jar, which holds the logo; empty draws no logo
 * @param logoEntry the logo's entry in {@code gameJar}; empty draws no logo
 */
public record EarlyWindowSettings(
        String title,
        int width,
        int height,
        int guiScale,
        boolean darkBackground,
        Optional<Path> gameJar,
        Optional<String> logoEntry) {

    /** The game's window size without {@code --width}/{@code --height} (its {@code Window.BASE_WIDTH}/{@code HEIGHT}). */
    static final int DEFAULT_WIDTH = 854;

    static final int DEFAULT_HEIGHT = 480;

    public EarlyWindowSettings {
        if (width <= 0 || height <= 0) {
            width = DEFAULT_WIDTH;
            height = DEFAULT_HEIGHT;
        }
        guiScale = Math.max(0, guiScale);
    }

    /**
     * The settings of the game started with {@code gameArguments}: its size from {@code --width}/{@code --height}, its
     * options from {@code options.txt} in {@code --gameDir} (the working directory without it).
     */
    public static EarlyWindowSettings forGame(
            String title, List<String> gameArguments, Optional<Path> gameJar, Optional<String> logoEntry) {
        int width = intArgument(gameArguments, "--width").orElse(DEFAULT_WIDTH);
        int height = intArgument(gameArguments, "--height").orElse(DEFAULT_HEIGHT);
        Path gameDirectory = argument(gameArguments, "--gameDir").map(Path::of).orElse(Path.of(""));
        List<String> options = List.of();
        try {
            Path file = gameDirectory.resolve("options.txt");
            if (Files.isRegularFile(file)) {
                options = Files.readAllLines(file, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            options = List.of();
        }
        int guiScale = option(options, "guiScale")
                .flatMap(EarlyWindowSettings::parseInt)
                .orElse(0);
        boolean dark = option(options, "darkMojangStudiosBackground")
                .map("true"::equals)
                .orElse(false);
        return new EarlyWindowSettings(title, width, height, guiScale, dark, gameJar, logoEntry);
    }

    /** These settings as the early window process's arguments. */
    List<String> toArguments() {
        List<String> arguments = new ArrayList<>(List.of(
                "--title", title,
                "--width", Integer.toString(width),
                "--height", Integer.toString(height),
                "--guiScale", Integer.toString(guiScale),
                "--dark", Boolean.toString(darkBackground)));
        gameJar.ifPresent(jar -> arguments.addAll(List.of("--gameJar", jar.toString())));
        logoEntry.ifPresent(entry -> arguments.addAll(List.of("--logo", entry)));
        return arguments;
    }

    /** The settings {@link #toArguments()} wrote. */
    static EarlyWindowSettings fromArguments(List<String> arguments) {
        return new EarlyWindowSettings(
                argument(arguments, "--title").orElse(LoadingScreenState.NAME),
                intArgument(arguments, "--width").orElse(DEFAULT_WIDTH),
                intArgument(arguments, "--height").orElse(DEFAULT_HEIGHT),
                intArgument(arguments, "--guiScale").orElse(0),
                argument(arguments, "--dark").map("true"::equals).orElse(false),
                argument(arguments, "--gameJar").map(Path::of),
                argument(arguments, "--logo"));
    }

    private static Optional<String> argument(List<String> arguments, String name) {
        for (int i = 0; i + 1 < arguments.size(); i++) {
            if (arguments.get(i).equals(name)) {
                return Optional.of(arguments.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> intArgument(List<String> arguments, String name) {
        return argument(arguments, name)
                .flatMap(EarlyWindowSettings::parseInt)
                .filter(value -> value > 0 || name.equals("--guiScale"));
    }

    // options.txt lines are <key>:<value>.
    private static Optional<String> option(List<String> lines, String key) {
        String prefix = key + ":";
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .reduce((first, last) -> last);
    }

    private static Optional<Integer> parseInt(String text) {
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
