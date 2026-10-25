package dev.polymodloader.tools.apireference;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.DocumentationTool;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Finds the release that added each element of the public API: {@code ApiSinceMain <repository> <output json>
 * <module>...} reads the API modules' sources at every release tag ({@code v*}, oldest first) with {@link
 * ApiSurfaceDoclet}, then the working tree's, and writes, for every element the working tree has, the first release
 * whose sources had it ({@code null} when no release had it yet). The API reference shows it ({@link MarkdownDoclet}).
 *
 * <p>The earliest tag stands for everything before it too: an element it has may be older.
 */
public final class ApiSinceMain {

    private ApiSinceMain() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: ApiSinceMain <repository> <output json> <module>...");
        }
        Path repository = Path.of(args[0]);
        Path output = Path.of(args[1]);
        List<String> modules = List.of(args).subList(2, args.length);
        List<String> tags = git(repository, "tag", "--list", "v*", "--sort=v:refname").lines()
                .filter(tag -> !tag.isBlank())
                .toList();
        Path work = Files.createTempDirectory("poly-api-since");
        Map<String, String> since = new LinkedHashMap<>();
        List<String> releases = new ArrayList<>();
        try {
            for (String tag : tags) {
                Path root = work.resolve(tag);
                List<Path> sources = extract(repository, tag, modules, root);
                if (sources.isEmpty()) {
                    // A tag without the API modules, such as one fetched from the public repository (README only).
                    System.out.println(tag + ": no API sources, skipped");
                    continue;
                }
                String release = tag.substring(1);
                releases.add(release);
                for (String key : surface(sources, work.resolve(tag + ".txt"))) {
                    since.putIfAbsent(key, release);
                }
                System.out.println(tag + ": " + since.size() + " element(s) so far");
            }
            List<Path> current = modules.stream()
                    .map(module -> repository.resolve(module).resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .toList();
            Set<String> now = surface(current, work.resolve("current.txt"));
            Files.writeString(output, json(releases, now, since), StandardCharsets.UTF_8);
            long unreleased = now.stream().filter(key -> !since.containsKey(key)).count();
            System.out.println("Wrote " + output + ": " + now.size() + " element(s), " + unreleased + " not released yet");
        } finally {
            try (Stream<Path> files = Files.walk(work)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    /** The modules' {@code src/main/java} at {@code tag}, extracted below {@code root}; modules the tag lacks are left out. */
    private static List<Path> extract(Path repository, String tag, List<String> modules, Path root)
            throws IOException, InterruptedException {
        List<String> paths = new ArrayList<>();
        for (String module : modules) {
            String path = module + "/src/main/java";
            if (!git(repository, "ls-tree", "-d", "--name-only", tag, path).isBlank()) {
                paths.add(path);
            }
        }
        Path zip = root.resolveSibling(tag + ".zip");
        Files.createDirectories(root);
        List<String> command = new ArrayList<>(List.of("archive", "--format=zip", "-o", zip.toString(), tag, "--"));
        command.addAll(paths);
        git(repository, command.toArray(String[]::new));
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Archive entry outside its folder: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target);
                }
            }
        }
        Files.delete(zip);
        return paths.stream().map(root::resolve).toList();
    }

    /** The API surface of {@code sources} ({@link ApiSurfaceDoclet}). */
    private static Set<String> surface(List<Path> sources, Path keysFile) throws IOException {
        System.setProperty(ApiSurfaceDoclet.OUTPUT_PROPERTY, keysFile.toString());
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager files = tool.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            files.setLocationFromPaths(StandardLocation.SOURCE_PATH, sources);
            List<String> options = List.of("-encoding", "UTF-8", "-quiet", "-subpackages", "dev");
            boolean ok = tool.getTask(null, files, null, ApiSurfaceDoclet.class, options, null).call();
            if (!ok) {
                throw new IllegalStateException("Javadoc failed on " + sources);
            }
        }
        return new TreeSet<>(Files.readAllLines(keysFile, StandardCharsets.UTF_8));
    }

    private static String json(List<String> releases, Set<String> now, Map<String, String> since) {
        StringBuilder out = new StringBuilder("{\n  \"releases\": [");
        for (int i = 0; i < releases.size(); i++) {
            out.append(i == 0 ? "" : ", ").append('"').append(releases.get(i)).append('"');
        }
        out.append("],\n  \"since\": {\n");
        int i = 0;
        for (String key : now) {
            String release = since.get(key);
            out.append("    \"")
                    .append(key.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\": ")
                    .append(release == null ? "null" : '"' + release + '"')
                    .append(++i < now.size() ? ",\n" : "\n");
        }
        return out.append("  }\n}\n").toString();
    }

    private static String git(Path repository, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String text;
        try (InputStream in = process.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (process.waitFor() != 0) {
            throw new UncheckedIOException(new IOException(String.join(" ", command) + " failed: " + text));
        }
        return text;
    }
}
