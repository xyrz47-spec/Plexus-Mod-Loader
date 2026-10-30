package dev.polymodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Starts support for a new Minecraft version by copying the modules of an existing one (ADR-0011):
 * {@code plexus-version-adapter-v<from>} and {@code plexus-game-v<from>} become {@code …-v<to>}, with the
 * version renamed in paths, package and class names, and text, and the version is added to {@value
 * MinecraftVersions#FILE}. The copy then shows, by what no longer compiles or passes, what changed in
 * the game.
 */
public final class VersionScaffolder {

    /** Directories of build output and runs, never copied. */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("build", ".gradle", "logs", "run", "out", "bin");

    /** Files whose text is rewritten; others are copied byte for byte. */
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("java", "kts", "json", "properties", "md", "txt", "mcmeta", "toml", "xml");

    private VersionScaffolder() {}

    /**
     * Copies the modules of {@code from} to {@code to} under {@code root} and lists {@code to} as
     * supported with {@code javaVersion}. Returns the created files.
     *
     * @throws IllegalArgumentException if {@code from} is not supported, {@code to} already is, or a
     *     target module directory exists
     */
    public static List<Path> scaffold(Path root, String from, String to, int javaVersion) {
        return scaffold(root, from, to, javaVersion, false);
    }

    /** Like {@link #scaffold(Path, String, String, int)}; with {@code experimental}, lists {@code to} as experimental. */
    public static List<Path> scaffold(Path root, String from, String to, int javaVersion, boolean experimental) {
        MinecraftVersions versions = MinecraftVersions.read(root);
        if (!versions.withModules().contains(from)) {
            throw new IllegalArgumentException(
                    "Cannot copy Minecraft " + from + ": it has no modules (" + versions.withModules() + ")");
        }
        if (versions.runnable().contains(to) || versions.isExperimental(to)) {
            throw new IllegalArgumentException("Minecraft " + to + " is already supported");
        }
        if (!to.matches("[0-9A-Za-z][0-9A-Za-z.\\-]*")) {
            throw new IllegalArgumentException("'" + to + "' is not a Minecraft version ID");
        }
        List<String[]> modules = List.of(
                new String[] {MinecraftVersions.adapterModule(from), MinecraftVersions.adapterModule(to)},
                new String[] {MinecraftVersions.gameLayerModule(from), MinecraftVersions.gameLayerModule(to)});
        for (String[] module : modules) {
            if (Files.exists(root.resolve(module[1]))) {
                throw new IllegalArgumentException(module[1] + " already exists");
            }
        }
        Renamer renamer = new Renamer(from, to);
        List<Path> created = new ArrayList<>();
        try {
            for (String[] module : modules) {
                copyModule(root.resolve(module[0]), root.resolve(module[1]), renamer, created);
            }
            addToVersionsFile(root.resolve(MinecraftVersions.FILE), to, javaVersion, experimental);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return created;
    }

    private static void copyModule(Path source, Path target, Renamer renamer, List<Path> created)
            throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(source)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(file -> !skipped(source.relativize(file)))
                    .toList();
        }
        for (Path file : files) {
            String relative = source.relativize(file).toString().replace('\\', '/');
            Path destination = target.resolve(renamer.rename(relative));
            Files.createDirectories(destination.getParent());
            if (TEXT_EXTENSIONS.contains(extension(file)) || file.toString().contains("META-INF/services")
                    || file.toString().contains("META-INF\\services")) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Files.writeString(destination, renamer.rename(text), StandardCharsets.UTF_8);
            } else {
                Files.copy(file, destination);
            }
            created.add(destination);
        }
    }

    private static boolean skipped(Path relative) {
        for (Path part : relative) {
            if (SKIPPED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    /** Adds {@code version} to {@code supported} and a {@code java.<version>} line, keeping comments. */
    static void addToVersionsFile(Path file, String version, int javaVersion) throws IOException {
        addToVersionsFile(file, version, javaVersion, false);
    }

    /**
     * Adds {@code version} to {@code supported}, or with {@code experimental} to {@code experimental} (a line
     * after {@code supported} if there is none), and a {@code java.<version>} line, keeping comments.
     */
    static void addToVersionsFile(Path file, String version, int javaVersion, boolean experimental)
            throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        String key = experimental ? "experimental=" : "supported=";
        int supportedLine = -1;
        boolean listed = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("supported=")) {
                supportedLine = i;
            }
            if (line.startsWith(key)) {
                String values = line.substring(key.length()).strip();
                lines.set(i, key + (values.isEmpty() ? version : values + "," + version));
                listed = true;
            }
        }
        if (!listed) {
            lines.add(supportedLine + 1, key + version);
        }
        int lastJava = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("java.")) {
                lastJava = i;
            }
        }
        String javaLine = "java." + version + "=" + javaVersion;
        if (lastJava < 0) {
            lines.add(javaLine);
        } else {
            lines.add(lastJava + 1, javaLine);
        }
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Renames one version to another in text: the version ID ({@code 26.2}) and the module ID form
     * used in package, class and module names ({@code 26_2}), each only as a whole token so {@code
     * 26.2.1} and {@code 126_2} stay untouched.
     */
    static final class Renamer {

        private final Pattern version;
        private final Pattern moduleId;
        private final String toVersion;
        private final String toModuleId;

        Renamer(String from, String to) {
            this.version = Pattern.compile("(?<![\\w.])" + Pattern.quote(from) + "(?!\\.?\\d|[\\w-])");
            this.moduleId = Pattern.compile("(?<![\\d])" + Pattern.quote(MinecraftVersions.moduleId(from)) + "(?!\\d)");
            this.toVersion = to;
            this.toModuleId = MinecraftVersions.moduleId(to);
        }

        String rename(String text) {
            String renamed = version.matcher(text).replaceAll(Matcher.quoteReplacement(toVersion));
            return moduleId.matcher(renamed).replaceAll(Matcher.quoteReplacement(toModuleId));
        }
    }
}
