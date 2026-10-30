package dev.plexusmodloader.devtools.gradle.minecraft;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads and links every class of a remapped Minecraft jar in an isolated class loader, without initializing
 * any (ADR-0019, "A remap self-check"). {@link Class#getDeclaredMethods()} links the class, so the bytecode
 * verifier checks each class once; a class whose remapped names do not fit together fails here with a
 * {@link LinkageError} instead of during a game start.
 *
 * <p>Runs as its own JVM ({@code remapMinecraft} starts it on the Java that runs the game), so the game's
 * classes never load into the Gradle daemon. Arguments: the remapped jar, then the libraries as one class
 * path. Prints {@code LOADED <count>} and one {@code FAILED <class>\t<error>} line per class that failed.
 */
public final class RemappedJarLoadCheck {

    /**
     * What loading found.
     *
     * @param failures {@code class<TAB>error} for each class that failed to load or link
     */
    record Result(int loaded, List<String> failures) {}

    private RemappedJarLoadCheck() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: RemappedJarLoadCheck <remapped jar> <library class path>");
        }
        List<Path> libraries = new ArrayList<>();
        for (String library : args[1].split(File.pathSeparator)) {
            if (!library.isBlank()) {
                libraries.add(Path.of(library));
            }
        }
        Result result = check(Path.of(args[0]), libraries);
        result.failures().forEach(failure -> System.out.println("FAILED " + failure));
        System.out.println("LOADED " + result.loaded());
    }

    /** Loads and links every class of {@code jar} in a new loader over it and {@code libraries}. */
    static Result check(Path jar, List<Path> libraries) throws IOException {
        List<URL> urls = new ArrayList<>(List.of(url(jar)));
        libraries.forEach(library -> urls.add(url(library)));
        List<String> names = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            for (JarEntry entry : file.stream().toList()) {
                String name = entry.getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class")) {
                    names.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
                }
            }
        }
        int loaded = 0;
        List<String> failures = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            for (String name : names) {
                try {
                    Class<?> type = Class.forName(name, false, loader);
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                    loaded++;
                } catch (LinkageError | ClassNotFoundException e) {
                    // Recorded, not hidden: each failure is reported and fails remapMinecraft.
                    failures.add(name + "\t" + e.getClass().getName() + ": "
                            + String.valueOf(e.getMessage()).replace('\n', ' '));
                }
            }
        }
        return new Result(loaded, List.copyOf(failures));
    }

    private static URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Not a valid path: " + path, e);
        }
    }
}
