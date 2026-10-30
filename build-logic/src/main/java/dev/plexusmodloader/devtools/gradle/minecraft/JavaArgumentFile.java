package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts {@code java} with its arguments in an argument file ({@code java @file}, JDK 9+) instead of on the command
 * line. Windows refuses a command line over 32,767 characters ({@code CreateProcess error=206}); a client with the
 * whole Quilt corpus in {@code poly.devMods} and Minecraft's libraries on {@code -cp} goes over it.
 */
final class JavaArgumentFile {

    private JavaArgumentFile() {}

    /**
     * Writes every argument after {@code command[0]} (the {@code java} executable) to {@code file}, one per line,
     * quoted as the launcher reads argument files (backslash and double quote escaped), and returns {@code [java,
     * @file]}.
     */
    static List<String> commandWithArgumentFile(List<String> command, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String argument : command.subList(1, command.size())) {
            lines.add(quote(argument));
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
        return List.of(command.getFirst(), "@" + file.toAbsolutePath());
    }

    static String quote(String argument) {
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
