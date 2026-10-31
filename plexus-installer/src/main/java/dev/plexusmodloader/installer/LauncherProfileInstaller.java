package dev.plexusmodloader.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Installs Plexus Mod Loader into a Minecraft Launcher folder ({@code .minecraft}), the way the launcher expects a
 * mod loader:
 *
 * <ol>
 *   <li>the Plexus Mod Loader jars into {@code libraries/} (checked by SHA-1)
 *   <li>a version {@code versions/plexusmodloader-<plexus>-<minecraft>/} that inherits the vanilla version and starts
 *       Plexus Mod Loader's launcher main class instead of Minecraft's
 *   <li>a profile "Plexus Mod Loader <minecraft>" in {@code launcher_profiles.json} (and the Microsoft Store
 *       launcher's {@code launcher_profiles_microsoft_store.json} when present), keeping everything else in the
 *       file
 *   <li>the {@code mods/} folder
 * </ol>
 *
 * <p>Installing again repairs or updates the same version and profile. Files are written to a temporary file
 * and moved into place, so an interrupted install leaves no half-written JSON.
 */
final class LauncherProfileInstaller {

    static final String PROFILES_FILE = "launcher_profiles.json";
    static final String STORE_PROFILES_FILE = "launcher_profiles_microsoft_store.json";

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final InstallerPayload payload;
    private final Clock clock;

    LauncherProfileInstaller(InstallerPayload payload, Clock clock) {
        this.payload = payload;
        this.clock = clock;
    }

    /** The launcher version ID Plexus Mod Loader installs for {@code minecraftVersion}. */
    String versionId(String minecraftVersion) {
        return "plexusmodloader-" + payload.plexusVersion() + "-" + minecraftVersion;
    }

    /** The key of the Plexus Mod Loader profile in {@code launcher_profiles.json}. */
    static String profileKey(String minecraftVersion) {
        return "plexusmodloader-" + minecraftVersion;
    }

    /** The key 1.4.x gave the same profile; installing moves that profile to {@link #profileKey}. */
    static String legacyProfileKey(String minecraftVersion) {
        return "polymodloader-" + minecraftVersion;
    }

    /**
     * Installs Plexus Mod Loader for {@code minecraftVersion}.
     *
     * @param minecraftDirectory the launcher folder, normally {@link MinecraftDirectories#defaultDirectory()}
     * @param createProfile whether to add the launcher profile
     * @param memory the game's {@code -Xms}/{@code -Xmx}, written to the profile's {@code javaArgs}
     * @param progress receives one line per step, for the window or the console
     * @return warnings the user should see; empty when all went as expected
     * @throws InstallerException when the folder or version cannot be used
     * @throws IOException when a file cannot be written
     */
    List<String> install(
            Path minecraftDirectory,
            String minecraftVersion,
            boolean createProfile,
            JavaMemory memory,
            Consumer<String> progress)
            throws InstallerException, IOException {
        checkVersion(minecraftVersion);
        if (!Files.isDirectory(minecraftDirectory)) {
            throw new InstallerException("The Minecraft folder " + minecraftDirectory
                    + " does not exist. Start the Minecraft Launcher once, or choose the folder it uses.");
        }
        List<Path> profileFiles = profileFiles(minecraftDirectory);
        if (createProfile && profileFiles.isEmpty()) {
            throw new InstallerException("No " + PROFILES_FILE + " in " + minecraftDirectory
                    + ". Start the Minecraft Launcher once so it creates the file, then install again.");
        }
        if (createProfile) {
            // A broken profile file stops the install before anything is written.
            for (Path file : profileFiles) {
                readProfiles(file);
            }
        }
        List<String> warnings = new ArrayList<>();
        Path vanilla = minecraftDirectory.resolve("versions").resolve(minecraftVersion);
        if (!Files.isRegularFile(vanilla.resolve(minecraftVersion + ".json"))) {
            warnings.add("Minecraft " + minecraftVersion + " has not been played from this launcher yet. If the"
                    + " Plexus Mod Loader profile does not start, play vanilla " + minecraftVersion
                    + " once and try again.");
        }
        if (!memory.isLauncherDefault() && !createProfile) {
            warnings.add("The memory setting was not written: it belongs to the launcher profile, which was not"
                    + " created. Set it in the launcher's profile settings (JVM arguments) instead.");
        }
        String memoryWarning = memory.warning(JavaMemory.physicalMegabytes());
        if (memoryWarning != null && createProfile) {
            warnings.add(memoryWarning);
        }

        Path libraries =
                minecraftDirectory.resolve("libraries").toAbsolutePath().normalize();
        for (InstallerPayload.Library library : payload.libraries()) {
            if (installLibrary(payload, libraries, library)) {
                progress.accept("Installed library " + library.name());
            }
        }
        progress.accept(payload.libraries().size() + " Plexus Mod Loader libraries are in " + libraries);

        String versionId = versionId(minecraftVersion);
        Path versionDirectory = minecraftDirectory.resolve("versions").resolve(versionId);
        Files.createDirectories(versionDirectory);
        writeAtomically(
                versionDirectory.resolve(versionId + ".json"),
                GSON.toJson(versionJson(versionId, minecraftVersion)).getBytes(StandardCharsets.UTF_8));
        writePlaceholderJar(versionDirectory.resolve(versionId + ".jar"));
        progress.accept("Wrote version " + versionId);

        if (createProfile) {
            if (!memory.isLauncherDefault()) {
                progress.accept("Game memory: " + String.join(" ", memory.arguments()));
            }
            for (Path file : profileFiles) {
                addProfile(file, minecraftVersion, versionId, memory);
                progress.accept(
                        "Added the profile \"Plexus Mod Loader " + minecraftVersion + "\" to " + file.getFileName());
            }
        }
        Path mods = minecraftDirectory.resolve("mods");
        Files.createDirectories(mods);
        progress.accept("Put mods into " + mods);
        return warnings;
    }

    /**
     * Removes the version and profile {@link #install} added. The libraries of this Plexus Mod Loader version are
     * removed too once no other Minecraft version uses them; {@code mods/} is left alone.
     */
    void uninstall(Path minecraftDirectory, String minecraftVersion, Consumer<String> progress)
            throws InstallerException, IOException {
        checkVersion(minecraftVersion);
        String versionId = versionId(minecraftVersion);
        Path versions = minecraftDirectory.resolve("versions");
        Path versionDirectory = versions.resolve(versionId);
        if (Files.isDirectory(versionDirectory)) {
            deleteRecursively(versionDirectory);
            progress.accept("Removed version " + versionId);
        }
        for (Path file : profileFiles(minecraftDirectory)) {
            if (removeProfile(file, minecraftVersion)) {
                progress.accept("Removed the profile \"Plexus Mod Loader " + minecraftVersion + "\" from "
                        + file.getFileName());
            }
        }
        if (!otherVersionsInstalled(versions)) {
            for (InstallerPayload.Library library : payload.libraries()) {
                if (library.name().startsWith("dev.plexusmodloader:")) {
                    Path jar = minecraftDirectory.resolve("libraries").resolve(library.path());
                    if (Files.deleteIfExists(jar)) {
                        deleteEmptyParents(jar.getParent(), minecraftDirectory.resolve("libraries"));
                    }
                }
            }
            progress.accept("Removed the Plexus Mod Loader " + payload.plexusVersion() + " libraries");
        }
    }

    private void checkVersion(String minecraftVersion) throws InstallerException {
        if (!payload.minecraftVersions().contains(minecraftVersion)) {
            throw new InstallerException("Plexus Mod Loader " + payload.plexusVersion() + " does not support Minecraft "
                    + minecraftVersion + "; it supports " + String.join(", ", payload.minecraftVersions()) + ".");
        }
    }

    private static List<Path> profileFiles(Path minecraftDirectory) {
        List<Path> files = new ArrayList<>();
        for (String name : List.of(PROFILES_FILE, STORE_PROFILES_FILE)) {
            Path file = minecraftDirectory.resolve(name);
            if (Files.isRegularFile(file)) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * Copies one of {@code payload}'s jars below {@code libraries}, checked by SHA-1 and size.
     *
     * @return true when the jar was copied, false when an identical copy was already there
     */
    static boolean installLibrary(InstallerPayload payload, Path libraries, InstallerPayload.Library library)
            throws InstallerException, IOException {
        Path target = libraries.resolve(library.path()).normalize();
        if (!target.startsWith(libraries)) {
            throw new InstallerException("Library path " + library.path() + " leaves the libraries folder");
        }
        if (Files.isRegularFile(target) && sha1(target).equals(library.sha1())) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path partial =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".partial");
        try {
            try (InputStream input = payload.source().open(library.path())) {
                Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            String actual = sha1(partial);
            if (!actual.equals(library.sha1()) || Files.size(partial) != library.size()) {
                throw new InstallerException("The installer's copy of " + library.name()
                        + " is damaged (SHA-1 " + actual + ", expected " + library.sha1()
                        + "). Download the installer again.");
            }
            move(partial, target);
        } finally {
            Files.deleteIfExists(partial);
        }
        return true;
    }

    /**
     * The launcher version: everything not listed here (game jar, assets, Java runtime, vanilla libraries and
     * arguments) comes from the vanilla version it inherits. The launcher appends these JVM arguments to the
     * vanilla ones and starts the process in the game folder, where Plexus Mod Loader finds {@code mods/}.
     */
    private JsonObject versionJson(String versionId, String minecraftVersion) {
        String now = timestamp();
        JsonObject version = new JsonObject();
        version.addProperty("id", versionId);
        version.addProperty("inheritsFrom", minecraftVersion);
        version.addProperty("releaseTime", now);
        version.addProperty("time", now);
        version.addProperty("type", "release");
        version.addProperty("mainClass", payload.mainClass());
        JsonObject arguments = new JsonObject();
        arguments.add("game", new JsonArray());
        JsonArray jvm = new JsonArray();
        jvm.add("-Dplexus.launchTarget=client");
        // Plexus Mod Loader's agent, from the library below; the launcher fills in ${library_directory} (ADR-0024).
        payload.agentLibrary().ifPresent(agent -> jvm.add("-javaagent:${library_directory}/" + agent.path()));
        arguments.add("jvm", jvm);
        version.add("arguments", arguments);
        JsonArray libraries = new JsonArray();
        for (InstallerPayload.Library library : payload.libraries()) {
            JsonObject artifact = new JsonObject();
            artifact.addProperty("path", library.path());
            artifact.addProperty("sha1", library.sha1());
            artifact.addProperty("size", library.size());
            // Installed by this installer, not downloaded: the launcher checks the file and its SHA-1 only.
            artifact.addProperty("url", "");
            JsonObject downloads = new JsonObject();
            downloads.add("artifact", artifact);
            JsonObject entry = new JsonObject();
            entry.addProperty("name", library.name());
            entry.add("downloads", downloads);
            libraries.add(entry);
        }
        version.add("libraries", libraries);
        return version;
    }

    private void addProfile(Path file, String minecraftVersion, String versionId, JavaMemory memory)
            throws InstallerException, IOException {
        JsonObject root = readProfiles(file);
        JsonObject profiles = root.has("profiles") && root.get("profiles").isJsonObject()
                ? root.getAsJsonObject("profiles")
                : new JsonObject();
        root.add("profiles", profiles);
        String key = profileKey(minecraftVersion);
        JsonElement legacy = profiles.remove(legacyProfileKey(minecraftVersion));
        JsonObject profile;
        if (profiles.has(key) && profiles.get(key).isJsonObject()) {
            profile = profiles.getAsJsonObject(key);
        } else if (legacy != null && legacy.isJsonObject()) {
            profile = legacy.getAsJsonObject();
        } else {
            profile = new JsonObject();
        }
        String now = timestamp();
        if (!profile.has("created")) {
            profile.addProperty("created", now);
        }
        profile.addProperty("icon", ProfileIcon.dataUri());
        profile.addProperty("lastUsed", now);
        profile.addProperty("lastVersionId", versionId);
        profile.addProperty("name", "Plexus Mod Loader " + minecraftVersion);
        profile.addProperty("type", "custom");
        if (!memory.isLauncherDefault()) {
            // The launcher passes javaArgs instead of its default "-Xmx2G ..." arguments.
            String existing = profile.has("javaArgs") && profile.get("javaArgs").isJsonPrimitive()
                    ? profile.get("javaArgs").getAsString()
                    : null;
            profile.addProperty("javaArgs", memory.mergeInto(existing));
        }
        profiles.add(key, profile);
        Files.copy(
                file,
                file.resolveSibling(file.getFileName() + ".plexusmodloader-backup"),
                StandardCopyOption.REPLACE_EXISTING);
        writeAtomically(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean removeProfile(Path file, String minecraftVersion) throws InstallerException, IOException {
        JsonObject root = readProfiles(file);
        if (!root.has("profiles") || !root.get("profiles").isJsonObject()) {
            return false;
        }
        JsonObject profiles = root.getAsJsonObject("profiles");
        JsonElement removed = profiles.remove(profileKey(minecraftVersion));
        JsonElement removedLegacy = profiles.remove(legacyProfileKey(minecraftVersion));
        if (removed == null && removedLegacy == null) {
            return false;
        }
        writeAtomically(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private static JsonObject readProfiles(Path file) throws InstallerException, IOException {
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new InstallerException(file + " is not a launcher profile file (no JSON object)");
            }
            return root.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new InstallerException(
                    file + " is not valid JSON, so the profile was not added. Start the"
                            + " Minecraft Launcher once to repair it, then install again.",
                    e);
        }
    }

    private boolean otherVersionsInstalled(Path versions) throws IOException {
        if (!Files.isDirectory(versions)) {
            return false;
        }
        String prefix = "plexusmodloader-" + payload.plexusVersion() + "-";
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(versions, prefix + "*")) {
            return entries.iterator().hasNext();
        }
    }

    /** Older launchers expect a jar in every version folder; the game jar itself comes from the vanilla version. */
    private static void writePlaceholderJar(Path jar) throws IOException {
        if (Files.isRegularFile(jar)) {
            return;
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        Path partial = Files.createTempFile(jar.getParent(), jar.getFileName().toString(), ".partial");
        try {
            try (OutputStream file = Files.newOutputStream(partial);
                    JarOutputStream output = new JarOutputStream(file, manifest)) {
                output.flush();
            }
            move(partial, jar);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    static void writeAtomically(Path file, byte[] content) throws IOException {
        Path partial = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".partial");
        try {
            Files.write(partial, content);
            move(partial, file);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String timestamp() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS).toString();
    }

    static String sha1(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-1", e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path :
                    paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void deleteEmptyParents(Path directory, Path stopAt) throws IOException {
        Path current = directory;
        while (current != null && current.startsWith(stopAt) && !current.equals(stopAt)) {
            try (Stream<Path> entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(current);
            current = current.getParent();
        }
    }
}
