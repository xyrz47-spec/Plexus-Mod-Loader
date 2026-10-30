package dev.plexusmodloader.earlywindow;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The lines the game's process writes to the early window's process (its standard input), one command per line:
 *
 * <ul>
 *   <li>{@code show}: open the window (it is prepared hidden, so that starting the process costs nothing visible);
 *   <li>{@code status <text>}: what Poly Mod Loader is doing, bottom left;
 *   <li>{@code progress <0..1>}: the bar; it never goes back;
 *   <li>{@code under <text>[\t<text>...]}: the lines under the bar;
 *   <li>{@code snapshot <file>}: write what the window shows to a PNG file (for checks);
 *   <li>{@code close}: close the window and end the process. The end of the input does the same.
 * </ul>
 *
 * Line breaks inside a text are replaced by spaces.
 */
final class EarlyWindowProtocol {

    sealed interface Command {}

    record Show() implements Command {}

    record Status(String text) implements Command {}

    record Progress(float value) implements Command {}

    record UnderBar(List<String> lines) implements Command {}

    record Snapshot(String file) implements Command {}

    record Close() implements Command {}

    private EarlyWindowProtocol() {}

    static String show() {
        return "show";
    }

    static String status(String text) {
        return "status " + oneLine(text);
    }

    static String progress(float value) {
        return "progress " + Math.max(0.0f, Math.min(1.0f, value));
    }

    static String underBar(List<String> lines) {
        return "under "
                + String.join(
                        "\t",
                        lines.stream()
                                .map(line -> oneLine(line).replace('\t', ' '))
                                .toList());
    }

    static String snapshot(String file) {
        return "snapshot " + oneLine(file);
    }

    static String close() {
        return "close";
    }

    /** The command of {@code line}; empty for a line that is not one (it is ignored). */
    static Optional<Command> parse(String line) {
        int space = line.indexOf(' ');
        String word = space < 0 ? line : line.substring(0, space);
        String rest = space < 0 ? "" : line.substring(space + 1);
        return switch (word) {
            case "show" -> Optional.of(new Show());
            case "close" -> Optional.of(new Close());
            case "status" -> Optional.of(new Status(rest));
            case "under" -> Optional.of(new UnderBar(rest.isEmpty() ? List.of() : Arrays.asList(rest.split("\t", -1))));
            case "snapshot" -> rest.isEmpty() ? Optional.empty() : Optional.of(new Snapshot(rest));
            case "progress" -> {
                try {
                    yield Optional.of(new Progress(Float.parseFloat(rest)));
                } catch (NumberFormatException e) {
                    yield Optional.empty();
                }
            }
            default -> Optional.empty();
        };
    }

    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ');
    }
}
