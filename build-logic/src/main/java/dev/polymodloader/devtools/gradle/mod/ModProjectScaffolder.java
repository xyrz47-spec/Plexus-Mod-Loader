package dev.polymodloader.devtools.gradle.mod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;

/**
 * Creates a new mod project from {@code templates/poly-mod-template}: copies it and renames the mod
 * ID, name, Java package, entry point class, and project name everywhere they appear, so the copy builds
 * as the new mod straight away ({@code newPolyMod}). {@link #renameInPlace} does the same to an unzipped
 * copy of the template ({@code initPolyMod}).
 */
public final class ModProjectScaffolder {

    /** Same rule as {@code poly.mod.json} ({@code dev.polymodloader.runtime.metadata.ModId}). */
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("build", ".gradle", "run", "out", ".idea", "bin");
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("java", "kts", "json", "properties", "md", "xml", "txt", "gitignore", "mcmeta");

    // The names the template uses, from the most specific to the least.
    static final String TEMPLATE_PACKAGE = "com.example.mymod";
    static final String TEMPLATE_CLASS = "MyMod";
    static final String TEMPLATE_PROJECT = "my-poly-mod";
    static final String TEMPLATE_NAME = "My Mod";
    static final String TEMPLATE_ID = "my_mod";
    static final String TEMPLATE_GROUP_LINE = "group = \"com.example\"";

    private ModProjectScaffolder() {}

    /** What the new project is called. */
    public record ModNames(String modId, String modName, String javaPackage) {

        /**
         * Checks the names.
         *
         * @throws IllegalArgumentException for a mod ID {@code poly.mod.json} rejects, a package that is not a
         *     Java package name, or a display name that cannot go into JSON as it is
         */
        public ModNames {
            if (modId == null || !MOD_ID.matcher(modId).matches()) {
                throw new IllegalArgumentException("'" + modId + "' is not a mod ID: start with a-z, then 1 to 63 of"
                        + " a-z, 0-9 and _ (such as ruby_tools)");
            }
            if (javaPackage == null || !SourceVersion.isName(javaPackage)) {
                throw new IllegalArgumentException(
                        "'" + javaPackage + "' is not a Java package name (such as io.github.you.rubytools)");
            }
            if (modName == null || modName.isBlank() || !modName.chars().allMatch(c -> c >= ' ' && c != '"' && c != '\\')) {
                throw new IllegalArgumentException("'" + modName + "' cannot be the mod name: write it without"
                        + " quotes, backslashes or line breaks (such as Ruby Tools)");
            }
        }

        /**
         * Names with the defaults of {@code newPolyMod} and {@code initPolyMod}: the display name is the mod ID
         * in title case, the package {@code com.example.<mod ID without _>}.
         *
         * @param modName the display name, or {@code null} for the default
         * @param javaPackage the Java package, or {@code null} for the default
         * @throws IllegalArgumentException as the constructor does
         */
        public static ModNames withDefaults(String modId, String modName, String javaPackage) {
            String id = modId == null ? "" : modId;
            return new ModNames(
                    modId,
                    modName != null ? modName : titleCase(id),
                    javaPackage != null ? javaPackage : "com.example." + id.replace("_", ""));
        }

        private static String titleCase(String modId) {
            StringBuilder name = new StringBuilder();
            for (String part : modId.split("_")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return name.toString();
        }

        /** The entry point class: the mod ID in UpperCamelCase, ending in {@code Mod}. */
        public String className() {
            StringBuilder name = new StringBuilder();
            for (String part : modId.split("_")) {
                if (!part.isEmpty()) {
                    name.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
                }
            }
            return name.toString().endsWith("Mod") ? name.toString() : name + "Mod";
        }

        /** The Gradle project name: the mod ID with dashes. */
        public String projectName() {
            return modId.replace('_', '-');
        }

        /** The Maven group: the package without its last part. */
        public String group() {
            int dot = javaPackage.lastIndexOf('.');
            return dot < 0 ? javaPackage : javaPackage.substring(0, dot);
        }

        String rename(String text) {
            return text.replace(TEMPLATE_PACKAGE, javaPackage)
                    .replace(TEMPLATE_PACKAGE.replace('.', '/'), javaPackage.replace('.', '/'))
                    .replace(TEMPLATE_CLASS, className())
                    .replace(TEMPLATE_PROJECT, projectName())
                    .replace(TEMPLATE_NAME, modName)
                    .replace(TEMPLATE_ID, modId)
                    .replace(TEMPLATE_GROUP_LINE, "group = \"" + group() + "\"");
        }
    }

    /**
     * Copies {@code template} to {@code target} with the template's names replaced by {@code names}.
     * Returns the created files.
     *
     * @throws IllegalArgumentException if {@code template} is not the mod template, or {@code target} exists
     *     and is not an empty directory
     */
    public static List<Path> scaffold(Path template, Path target, ModNames names) {
        if (!Files.isRegularFile(template.resolve("src/main/resources/poly.mod.json"))) {
            throw new IllegalArgumentException(template + " is not the Poly Mod Loader mod template");
        }
        try {
            if (Files.exists(target) && (!Files.isDirectory(target) || !isEmpty(target))) {
                throw new IllegalArgumentException(target + " already exists and is not empty; choose another folder");
            }
            List<Path> created = new ArrayList<>();
            for (Path file : templateFiles(template)) {
                String relative = template.relativize(file).toString().replace('\\', '/');
                Path destination = target.resolve(names.rename(relative));
                Files.createDirectories(destination.getParent());
                if (TEXT_EXTENSIONS.contains(extension(file))) {
                    Files.writeString(
                            destination,
                            names.rename(Files.readString(file, StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8);
                } else {
                    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
                created.add(destination);
            }
            return created;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Renames an unzipped copy of the template in place ({@code initPolyMod}): the same replacements as {@link
     * #scaffold}, with files moved to their renamed paths and the template's emptied folders removed. Folders
     * {@code scaffold} skips ({@code build}, {@code .gradle}, {@code run}, ...) are left alone. Returns the files
     * written or moved.
     *
     * @throws IllegalArgumentException if {@code project} is not an untouched copy of the template (its entry
     *     point {@code com.example.mymod.MyMod} and {@code poly.mod.json} must still be there)
     */
    public static List<Path> renameInPlace(Path project, ModNames names) {
        Path entryPoint = project.resolve(
                "src/main/java/" + TEMPLATE_PACKAGE.replace('.', '/') + "/" + TEMPLATE_CLASS + ".java");
        if (!Files.isRegularFile(project.resolve("src/main/resources/poly.mod.json"))
                || !Files.isRegularFile(entryPoint)) {
            throw new IllegalArgumentException(project + " is not an unchanged copy of the Poly Mod Loader mod template ("
                    + project.relativize(entryPoint).toString().replace('\\', '/')
                    + " is missing); it was renamed already, or is another project");
        }
        try {
            List<Path> files = templateFiles(project);
            // Check every move first, so a conflict leaves the project as it was.
            for (Path file : files) {
                Path destination = renamed(project, file, names);
                if (!destination.equals(file) && Files.exists(destination)) {
                    throw new IllegalArgumentException(
                            destination + " already exists; move it away or start from a fresh copy of the template");
                }
            }
            List<Path> changed = new ArrayList<>();
            List<Path> emptied = new ArrayList<>();
            for (Path file : files) {
                Path destination = renamed(project, file, names);
                boolean moved = !destination.equals(file);
                if (moved) {
                    Files.createDirectories(destination.getParent());
                }
                if (TEXT_EXTENSIONS.contains(extension(file))) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String renamed = names.rename(text);
                    if (moved || !renamed.equals(text)) {
                        Files.writeString(destination, renamed, StandardCharsets.UTF_8);
                        changed.add(destination);
                    }
                    if (moved) {
                        Files.delete(file);
                        emptied.add(file.getParent());
                    }
                } else if (moved) {
                    Files.move(file, destination);
                    changed.add(destination);
                    emptied.add(file.getParent());
                }
            }
            for (Path directory : emptied) {
                for (Path current = directory;
                        current != null && !current.equals(project) && Files.isDirectory(current) && isEmpty(current);
                        current = current.getParent()) {
                    Files.delete(current);
                }
            }
            return changed;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path renamed(Path root, Path file, ModNames names) {
        return root.resolve(names.rename(root.relativize(file).toString().replace('\\', '/')));
    }

    private static List<Path> templateFiles(Path template) throws IOException {
        try (Stream<Path> walk = Files.walk(template)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> {
                        for (Path part : template.relativize(file)) {
                            if (SKIPPED_DIRECTORIES.contains(part.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .toList();
        }
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
