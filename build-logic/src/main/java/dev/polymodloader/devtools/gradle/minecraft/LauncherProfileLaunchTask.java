package dev.polymodloader.devtools.gradle.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * Checks the end-user installation path: runs the installer jar ({@code install --dir}) on a fresh launcher
 * folder, then starts the installed profile the way the Minecraft Launcher does and waits for the player to
 * join a saved world (opens a window).
 *
 * <p>The launcher's work is reproduced from the files the installer wrote: the profile in
 * {@code launcher_profiles.json} names a version; that version is merged with the vanilla version it inherits
 * (libraries of both, then the vanilla game jar on the class path; JVM and game arguments of the vanilla
 * version followed by the profile's, then the profile's {@code javaArgs}; the profile's main class); library
 * rules and launcher variables are resolved; the vanilla log configuration is passed; the process starts in the
 * game folder. Poly Mod Loader's libraries must be where the version says, with the SHA-1 it says, and no library may
 * appear twice.
 *
 * <p>The vanilla files come from Poly Mod Loader's verified download cache (the {@code prepareMinecraftClient}
 * launch spec) instead of a real launcher installation; nothing is written to the user's {@code .minecraft}.
 */
@DisableCachingByDefault(because = "Runs the game and checks its output")
public abstract class LauncherProfileLaunchTask extends DefaultTask {

    private static final String PLAYER = "PolyDev";
    private static final String MAXIMUM_MEMORY = "3G";
    private static final String MINIMUM_MEMORY = "1G";

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInstallerJar();

    /** Launch spec of the prepared client: locates the download cache and the game version. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getLaunchSpecFile();

    /** Mod jars to put into the profile's {@code mods/} folder, as a player would. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getModJars();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceWorld();

    @Input
    public abstract Property<String> getWorldName();

    @Internal
    public abstract DirectoryProperty getRunDirectory();

    @Input
    public abstract ListProperty<String> getExpectedOutput();

    @Input
    public abstract Property<Integer> getTimeoutSeconds();

    /**
     * Paths below the game folder that must not exist after the run, such as {@code .poly/forge}: without Forge mods,
     * nothing of Forge is downloaded (ADR-0024).
     */
    @Input
    public abstract ListProperty<String> getAbsentPaths();

    @TaskAction
    public void run() throws IOException, InterruptedException {
        MinecraftLaunchSpec spec = MinecraftLaunchSpec.read(getLaunchSpecFile().get().getAsFile().toPath());
        String gameVersion = spec.gameVersion();
        Path cache = Path.of(spec.gameJar()).getParent().getParent().getParent();
        Path minecraft = getRunDirectory().get().getAsFile().toPath();
        VerifiedGameRunTask.deleteRecursively(minecraft);
        Files.createDirectories(minecraft);

        // The folder a player has after starting vanilla once: the launcher's profile file and the vanilla version.
        Files.writeString(
                minecraft.resolve("launcher_profiles.json"),
                "{\"profiles\": {\"vanilla\": {\"name\": \"Latest release\", \"type\": \"latest-release\"}},"
                        + " \"settings\": {}, \"version\": 3}",
                StandardCharsets.UTF_8);
        Path vanillaDirectory = minecraft.resolve("versions").resolve(gameVersion);
        Files.createDirectories(vanillaDirectory);
        Files.copy(
                cache.resolve("versions").resolve(gameVersion).resolve(gameVersion + ".json"),
                vanillaDirectory.resolve(gameVersion + ".json"));
        Files.copy(Path.of(spec.gameJar()), vanillaDirectory.resolve(gameVersion + ".jar"));

        install(minecraft, gameVersion);

        JsonObject profiles = readJson(minecraft.resolve("launcher_profiles.json"));
        JsonObject all = profiles.getAsJsonObject("profiles");
        if (!all.has("vanilla")) {
            throw new GradleException("The installer dropped an existing launcher profile");
        }
        JsonObject profile = all.getAsJsonObject("polymodloader-" + gameVersion);
        if (profile == null) {
            throw new GradleException("The installer added no profile polymodloader-" + gameVersion);
        }
        String javaArgs = profile.has("javaArgs") ? profile.get("javaArgs").getAsString() : "";
        if (!javaArgs.equals("-Xms" + MINIMUM_MEMORY + " -Xmx" + MAXIMUM_MEMORY)) {
            throw new GradleException("The installer wrote the profile's javaArgs as '" + javaArgs + "', not the memory"
                    + " it was given (-Xms" + MINIMUM_MEMORY + " -Xmx" + MAXIMUM_MEMORY + ")");
        }
        String versionId = profile.get("lastVersionId").getAsString();
        JsonObject child = readJson(minecraft.resolve("versions").resolve(versionId).resolve(versionId + ".json"));
        String parentId = child.get("inheritsFrom").getAsString();
        JsonObject parent = readJson(minecraft.resolve("versions").resolve(parentId).resolve(parentId + ".json"));

        List<String> classpath = classpath(minecraft, cache, child, parent);
        classpath.add(minecraft.resolve("versions").resolve(parentId).resolve(parentId + ".jar").toString());

        Path natives = minecraft.resolve("natives");
        Files.createDirectories(natives);
        String offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + PLAYER).getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        Map<String, String> variables = new HashMap<>();
        variables.put("natives_directory", natives.toString());
        variables.put("launcher_name", "polymodloader-profile-test");
        variables.put("launcher_version", "1");
        variables.put("classpath", String.join(File.pathSeparator, classpath));
        variables.put("classpath_separator", File.pathSeparator);
        variables.put("library_directory", minecraft.resolve("libraries").toString());
        variables.put("version_name", versionId);
        variables.put("game_directory", minecraft.toString());
        variables.put("assets_root", cache.resolve("assets").toString());
        variables.put("assets_index_name", parent.get("assets").getAsString());
        variables.put("auth_player_name", PLAYER);
        variables.put("auth_uuid", offlineUuid);
        variables.put("auth_access_token", "0");
        variables.put("clientid", "0");
        variables.put("auth_xuid", "0");
        variables.put("user_type", "msa");
        variables.put("version_type", child.has("type") ? child.get("type").getAsString() : "release");

        List<String> command = new ArrayList<>();
        command.add(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        command.addAll(LibraryRules.resolveArguments(arguments(parent, "jvm"), variables));
        command.addAll(LibraryRules.resolveArguments(arguments(child, "jvm"), variables));
        // The profile's "JVM arguments", which replace the launcher's default -Xmx2G.
        command.addAll(List.of(javaArgs.split(" ")));
        command.add(logConfigArgument(parent, cache));
        command.add(child.has("mainClass") ? child.get("mainClass").getAsString() : parent.get("mainClass").getAsString());
        command.addAll(LibraryRules.resolveArguments(arguments(parent, "game"), variables));
        command.addAll(LibraryRules.resolveArguments(arguments(child, "game"), variables));
        // The launcher's Quick Play feature, as when a player picks the world from the launcher.
        command.add("--quickPlaySingleplayer");
        command.add(getWorldName().get());

        ClientWorldReloadTask.copyDirectory(
                getSourceWorld().get().getAsFile().toPath(), minecraft.resolve("saves").resolve(getWorldName().get()));
        // As in ClientWorldReloadTask: a 26.3 game test world would stop Quick Play at a backup prompt.
        StableVanillaDimensions.apply(minecraft.resolve("saves").resolve(getWorldName().get()));
        for (File mod : getModJars().getFiles()) {
            Files.copy(mod.toPath(), minecraft.resolve("mods").resolve(mod.getName()));
        }
        Files.writeString(
                minecraft.resolve("options.txt"), "onboardAccessibility:false\ntutorialStep:none\n", StandardCharsets.UTF_8);
        runAndVerify(command, minecraft);
    }

    private void install(Path minecraft, String gameVersion) throws IOException, InterruptedException {
        List<String> command = List.of(
                getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath(),
                "-Djava.awt.headless=true",
                "-jar",
                getInstallerJar().get().getAsFile().getAbsolutePath(),
                "install",
                "--dir",
                minecraft.toString(),
                "--minecraft",
                gameVersion,
                "--max-memory",
                MAXIMUM_MEMORY,
                "--min-memory",
                MINIMUM_MEMORY);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        getLogger().lifecycle(output.strip());
        if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
            throw new GradleException("The installer failed:\n" + output);
        }
    }

    /**
     * Libraries of the profile's version, then of the vanilla version, as the launcher lists them. The
     * Poly Mod Loader libraries must be installed with the recorded SHA-1; vanilla ones come from the cache.
     */
    private static List<String> classpath(Path minecraft, Path cache, JsonObject child, JsonObject parent) {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> classpath = new ArrayList<>();
        for (JsonObject version : List.of(child, parent)) {
            boolean installed = version == child;
            for (JsonElement element : version.getAsJsonArray("libraries")) {
                JsonObject library = element.getAsJsonObject();
                if (!LibraryRules.allows(library.getAsJsonArray("rules"))) {
                    continue;
                }
                String[] name = library.get("name").getAsString().split(":");
                String key = name[0] + ":" + name[1] + (name.length > 3 ? ":" + name[3] : "");
                String previous = seen.put(key, library.get("name").getAsString());
                if (previous != null) {
                    throw new GradleException("The launcher would put " + key + " on the class path twice: " + previous
                            + " and " + library.get("name").getAsString());
                }
                JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
                String path = artifact.get("path").getAsString();
                Path jar = (installed ? minecraft.resolve("libraries") : cache.resolve("libraries")).resolve(path);
                if (!Files.isRegularFile(jar)) {
                    throw new GradleException("Library " + library.get("name").getAsString() + " is missing at " + jar);
                }
                if (installed) {
                    String actual = VerifiedDownloader.hash(jar, "SHA-1");
                    if (!actual.equalsIgnoreCase(artifact.get("sha1").getAsString())) {
                        throw new GradleException("Library " + jar + " has SHA-1 " + actual + " but the version says "
                                + artifact.get("sha1").getAsString());
                    }
                }
                classpath.add(jar.toString());
            }
        }
        return classpath;
    }

    private static JsonArray arguments(JsonObject version, String kind) {
        if (!version.has("arguments") || !version.getAsJsonObject("arguments").has(kind)) {
            return new JsonArray();
        }
        return version.getAsJsonObject("arguments").getAsJsonArray(kind);
    }

    /** The vanilla log4j configuration the launcher passes, downloaded and checked like the launcher does. */
    private static String logConfigArgument(JsonObject parent, Path cache) {
        JsonObject client = parent.getAsJsonObject("logging").getAsJsonObject("client");
        JsonObject file = client.getAsJsonObject("file");
        Path target = cache.resolve("assets").resolve("log_configs").resolve(file.get("id").getAsString());
        if (!Files.isRegularFile(target)
                || !VerifiedDownloader.hash(target, "SHA-1").equalsIgnoreCase(file.get("sha1").getAsString())) {
            new VerifiedDownloader()
                    .downloadSha1Verified(
                            URI.create(file.get("url").getAsString()), file.get("sha1").getAsString(), target);
        }
        return client.get("argument").getAsString().replace("${path}", target.toString());
    }

    private void runAndVerify(List<String> command, Path minecraft) throws IOException, InterruptedException {
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(getExpectedOutput().get());
        Path outputLog = minecraft.resolve("launcher-profile-output.log");
        Process process = new ProcessBuilder(command)
                .directory(minecraft.toFile())
                .redirectErrorStream(true)
                .start();
        Thread reader = Thread.ofPlatform().name("profile-output").start(() -> {
            try (BufferedReader lines =
                            new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    var log = Files.newBufferedWriter(outputLog, StandardCharsets.UTF_8)) {
                String line;
                while ((line = lines.readLine()) != null) {
                    log.write(line);
                    log.newLine();
                    getLogger().info(line);
                    String current = line;
                    remaining.removeIf(current::contains);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(getTimeoutSeconds().get());
        while (!remaining.isEmpty() && process.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
        boolean exitedEarly = !process.isAlive();
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(30, TimeUnit.SECONDS);
        reader.join(TimeUnit.SECONDS.toMillis(10));
        if (!remaining.isEmpty()) {
            throw new GradleException("Launcher profile check failed"
                    + (exitedEarly ? " (the game exited with " + process.exitValue() + ")" : "")
                    + "; missing log lines: " + remaining.stream().sorted().collect(Collectors.joining(", "))
                    + ". Full output: " + outputLog + ClientWorldReloadTask.crashReports(minecraft));
        }
        List<String> present = getAbsentPaths().get().stream()
                .filter(path -> Files.exists(minecraft.resolve(path)))
                .toList();
        if (!present.isEmpty()) {
            throw new GradleException("Launcher profile check failed; the run left " + present + " in " + minecraft
                    + ", which it must not create");
        }
        getLogger()
                .lifecycle(
                        "Launcher profile check passed: installed, started like the Minecraft Launcher, joined '{}' ({} log"
                                + " lines verified)",
                        getWorldName().get(),
                        getExpectedOutput().get().size());
    }

    private static JsonObject readJson(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
