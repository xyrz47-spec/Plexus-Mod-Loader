package dev.polymodloader.devtools.gradle.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

/**
 * The Minecraft versions a Poly Mod Loader build supports, read from {@code gradle/minecraft-versions.properties}
 * (ADR-0011): the list, the default version, the Java version each one runs on, and the hotfixes each
 * one's modules also run. Version modules are named {@code poly-version-adapter-v<id>} and {@code
 * poly-game-v<id>} with {@link #moduleId}.
 *
 * <p>A hotfix ({@code hotfixes.<version>}) has no modules of its own: its game jar runs on the modules of
 * the version it is listed under, whose adapter lists it in {@code GameVersionAdapter.gameVersions()}.
 * It can be picked for runs like a supported version.
 *
 * <p>An experimental version ({@code experimental}) has its own modules, which the build compiles and tests,
 * and can be picked for development runs with {@value #SELECT_PROPERTY}. It is not supported: the installer
 * does not offer it, the SDK does not publish its modules and nothing may claim it works (ADR-0019 point 8).
 * Versions before 26.1 start here while their remap and game layer are proven (ADR-0019).
 *
 * @param supported supported versions, oldest first; each has its own modules
 * @param defaultVersion the version used when a build does not pick one
 * @param javaVersions Java feature version per supported or experimental version
 * @param hotfixes per supported version, the hotfix versions its modules also run, oldest first
 * @param experimental versions with their own modules that are not supported, oldest first
 */
public record MinecraftVersions(
        List<String> supported,
        String defaultVersion,
        Map<String, Integer> javaVersions,
        Map<String, List<String>> hotfixes,
        List<String> experimental) {

    /** The file, relative to the repository root. */
    public static final String FILE = "gradle/minecraft-versions.properties";

    /** Gradle property that picks the version for one build, e.g. {@code -PpolyMinecraft=26.2}. */
    public static final String SELECT_PROPERTY = "polyMinecraft";

    private static final String GAME_LAYER_PREFIX = "poly-game-v";
    private static final String ADAPTER_PREFIX = "poly-version-adapter-v";

    public MinecraftVersions {
        supported = List.copyOf(supported);
        experimental = List.copyOf(experimental);
        javaVersions = Map.copyOf(javaVersions);
        hotfixes = hotfixes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        if (supported.isEmpty()) {
            throw new IllegalArgumentException("'supported' lists no Minecraft version");
        }
        if (!supported.contains(defaultVersion)) {
            throw new IllegalArgumentException(
                    "'default' is " + defaultVersion + ", which is not in 'supported' " + supported);
        }
        for (String version : experimental) {
            if (supported.contains(version)) {
                throw new IllegalArgumentException(
                        "Minecraft " + version + " is in both 'supported' and 'experimental'");
            }
        }
        for (String version : withModules(supported, experimental)) {
            if (!javaVersions.containsKey(version)) {
                throw new IllegalArgumentException("'java." + version + "' is missing");
            }
        }
        List<String> seen = new ArrayList<>(withModules(supported, experimental));
        for (Map.Entry<String, List<String>> entry : hotfixes.entrySet()) {
            if (!supported.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "'hotfixes." + entry.getKey() + "' names a version that is not in 'supported' " + supported);
            }
            for (String hotfix : entry.getValue()) {
                if (seen.contains(hotfix)) {
                    throw new IllegalArgumentException("Minecraft " + hotfix + " is listed twice");
                }
                seen.add(hotfix);
            }
        }
    }

    /** Versions without hotfixes. */
    public MinecraftVersions(List<String> supported, String defaultVersion, Map<String, Integer> javaVersions) {
        this(supported, defaultVersion, javaVersions, Map.of(), List.of());
    }

    /** Versions without experimental ones. */
    public MinecraftVersions(
            List<String> supported,
            String defaultVersion,
            Map<String, Integer> javaVersions,
            Map<String, List<String>> hotfixes) {
        this(supported, defaultVersion, javaVersions, hotfixes, List.of());
    }

    /** Reads {@code properties} in the format of {@value #FILE}. */
    public static MinecraftVersions parse(Properties properties) {
        List<String> supported = list(properties.getProperty("supported", ""));
        List<String> experimental = list(properties.getProperty("experimental", ""));
        // distinct(): a version in both lists is reported by the constructor, not as a duplicate map key.
        Map<String, Integer> java = withModules(supported, experimental).stream()
                .distinct()
                .filter(version -> properties.getProperty("java." + version) != null)
                .collect(Collectors.toMap(
                        Function.identity(),
                        version -> Integer.parseInt(properties.getProperty("java." + version).strip())));
        Map<String, List<String>> hotfixes = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("hotfixes."))
                .collect(Collectors.toMap(key -> key.substring("hotfixes.".length()), key -> Arrays.stream(
                                properties.getProperty(key).split(","))
                        .map(String::strip)
                        .filter(version -> !version.isEmpty())
                        .toList()));
        return new MinecraftVersions(
                supported, properties.getProperty("default", "").strip(), java, hotfixes, experimental);
    }

    private static List<String> list(String value) {
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(version -> !version.isEmpty())
                .toList();
    }

    private static List<String> withModules(List<String> supported, List<String> experimental) {
        List<String> all = new ArrayList<>(supported);
        all.addAll(experimental);
        return List.copyOf(all);
    }

    /** Every version with its own modules: the supported ones, then the experimental ones. */
    public List<String> withModules() {
        return withModules(supported, experimental);
    }

    /** Whether {@code version} is experimental: it has modules but is not supported. */
    public boolean isExperimental(String version) {
        return experimental.contains(version);
    }

    /** Reads the file from {@code repositoryRoot}. */
    public static MinecraftVersions read(Path repositoryRoot) {
        Path file = repositoryRoot.resolve(FILE);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        try {
            return parse(properties);
        } catch (IllegalArgumentException e) {
            throw new GradleException(file + ": " + e.getMessage(), e);
        }
    }

    /** The versions this plugin was built with, for mod projects outside the Poly Mod Loader repository. */
    public static MinecraftVersions bundled() {
        Properties properties = new Properties();
        try (InputStream input = MinecraftVersions.class.getResourceAsStream("minecraft-versions.properties")) {
            if (input == null) {
                throw new IllegalStateException("minecraft-versions.properties is missing from the Poly Mod Loader Gradle plugin");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parse(properties);
    }

    /** The versions of the repository {@code project} belongs to. */
    public static MinecraftVersions of(Project project) {
        return read(project.getRootDir().toPath());
    }

    /** The module ID of {@code version}: {@code 26.2} becomes {@code 26_2}. */
    public static String moduleId(String version) {
        return version.replace('.', '_').replace('-', '_');
    }

    /** The game layer module of {@code version}, e.g. {@code poly-game-v26_2}. */
    public static String gameLayerModule(String version) {
        return GAME_LAYER_PREFIX + moduleId(version);
    }

    /** The version adapter module of {@code version}, e.g. {@code poly-version-adapter-v26_2}. */
    public static String adapterModule(String version) {
        return ADAPTER_PREFIX + moduleId(version);
    }

    /** The game layer entry class of {@code version}, by the naming the version modules follow. */
    public static String gameLayerClass(String version) {
        String id = moduleId(version);
        return "dev.polymodloader.game.layer.v" + id + ".GameLayer" + id;
    }

    /**
     * Every version a build can run, oldest first: each supported version followed by its hotfixes.
     * The installer offers these.
     */
    public List<String> runnable() {
        List<String> all = new ArrayList<>();
        for (String version : supported) {
            all.add(version);
            all.addAll(hotfixes.getOrDefault(version, List.of()));
        }
        return List.copyOf(all);
    }

    /**
     * The supported version whose modules run {@code version}: the version itself, or the version a
     * hotfix is listed under.
     */
    public String modulesVersion(String version) {
        if (supported.contains(version) || experimental.contains(version)) {
            return version;
        }
        return hotfixes.entrySet().stream()
                .filter(entry -> entry.getValue().contains(version))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> unsupported(version));
    }

    /** The Java feature version {@code version} (supported or hotfix) runs on. */
    public int javaVersion(String version) {
        Integer java = javaVersions.get(modulesVersion(version));
        if (java == null) {
            throw unsupported(version);
        }
        return java;
    }

    /**
     * The version picked with {@value #SELECT_PROPERTY} (supported, hotfix or experimental), or the default.
     */
    public String selected(Project project) {
        Object picked = project.findProperty(SELECT_PROPERTY);
        if (picked == null) {
            return defaultVersion;
        }
        String version = picked.toString().strip();
        if (!runnable().contains(version) && !experimental.contains(version)) {
            throw unsupported(version);
        }
        return version;
    }

    /**
     * The version a version module ({@code poly-game-v<id>} or {@code poly-version-adapter-v<id>}) is
     * for, found from its name.
     */
    public String ofModule(Project project) {
        return versionOfModule(project.getName())
                .orElseThrow(() -> new GradleException(project.getPath()
                        + " is not named after a supported Minecraft version (" + GAME_LAYER_PREFIX + "<id> or "
                        + ADAPTER_PREFIX + "<id> with <id> one of " + withModules().stream()
                                .map(MinecraftVersions::moduleId)
                                .toList()
                        + ")"));
    }

    Optional<String> versionOfModule(String moduleName) {
        String id;
        if (moduleName.startsWith(GAME_LAYER_PREFIX)) {
            id = moduleName.substring(GAME_LAYER_PREFIX.length());
        } else if (moduleName.startsWith(ADAPTER_PREFIX)) {
            id = moduleName.substring(ADAPTER_PREFIX.length());
        } else {
            return Optional.empty();
        }
        return withModules().stream()
                .filter(version -> moduleId(version).equals(id))
                .findFirst();
    }

    private GradleException unsupported(String version) {
        return new GradleException("Minecraft " + version + " is not supported by this Poly Mod Loader build; supported: "
                + runnable() + (experimental.isEmpty() ? "" : ", experimental: " + experimental) + " (" + FILE + ")");
    }
}
