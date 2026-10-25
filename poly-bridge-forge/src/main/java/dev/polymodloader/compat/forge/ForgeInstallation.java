package dev.polymodloader.compat.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.polymodloader.api.runtime.GameSide;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Forge's files for one Minecraft version, put together on the player's PC the way Forge's installer does (ADR-0021
 * G3c): the installer from Forge's Maven (checked against the SHA-1 Poly Mod Loader pins), the runtime libraries its
 * {@code version.json} lists (each checked against the SHA-1 it gives), and the game jar with Forge's binary patches
 * applied (G2). Everything is cached in one folder per Forge version; a later launch only checks hashes. Poly Mod Loader
 * itself ships none of these files.
 *
 * <p>Thread: one launch prepares at a time; the methods are not safe for concurrent use on the same folder.
 */
final class ForgeInstallation {

    static final String MAVEN = "https://maven.minecraftforge.net/";

    /** The Forge release Poly Mod Loader uses for a Minecraft version ({@code forge-releases.properties}). */
    record Release(String minecraftVersion, String forgeVersion, String installerSha1) {

        URI installerUri() {
            return URI.create(
                    MAVEN + "net/minecraftforge/forge/" + forgeVersion + "/forge-" + forgeVersion + "-installer.jar");
        }
    }

    /**
     * The prepared files.
     *
     * @param patchedGame the game jar with Forge's patches; the game layer's {@code minecraft} module
     * @param libraries Forge's runtime libraries in {@code version.json} order, Forge's {@code universal.jar} among
     *     them
     * @param fileSystemJars the four of them that must be on the JVM class path ({@link ForgeFileSystems})
     */
    record Prepared(
            Release release, Path directory, Path patchedGame, List<Path> libraries, List<Path> fileSystemJars) {}

    /** Downloads one file; tests replace it. */
    @FunctionalInterface
    interface Downloader {
        void download(URI uri, Path target) throws IOException;
    }

    private ForgeInstallation() {}

    /** The Forge release for {@code minecraftVersion}, or empty when Poly Mod Loader has none for it. */
    static Optional<Release> release(String minecraftVersion) {
        Properties releases = new Properties();
        try (InputStream in = ForgeInstallation.class.getResourceAsStream("forge-releases.properties")) {
            if (in == null) {
                return Optional.empty();
            }
            releases.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read forge-releases.properties", e);
        }
        String value = releases.getProperty(minecraftVersion);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.strip().split("\\s+");
        return Optional.of(new Release(minecraftVersion, parts[0], parts[1]));
    }

    /**
     * Downloads what is missing or damaged and builds the patched game jar when the vanilla jar changed.
     *
     * @param cacheRoot the folder that holds one folder per Forge version
     * @param side which game jar {@code vanillaGame} is; Forge has patches for each
     * @param vanillaGame the game jar the patches apply to (the dedicated server's classes, or the client jar)
     * @param log receives one line per step
     * @throws IOException when a download fails, a file does not have the expected SHA-1, or a patch does not apply
     */
    static Prepared prepare(
            Release release,
            Path cacheRoot,
            GameSide side,
            Path vanillaGame,
            Downloader downloader,
            Consumer<String> log)
            throws IOException {
        Path directory = cacheRoot.resolve(release.forgeVersion());
        Files.createDirectories(directory);
        Path installer = directory.resolve("forge-" + release.forgeVersion() + "-installer.jar");
        ensure(installer, release.installerSha1(), file -> downloader.download(release.installerUri(), file), log);

        List<Path> libraries = new ArrayList<>();
        String sideName = side == GameSide.CLIENT ? "client" : "server";
        Path patched = directory.resolve("forge-" + release.forgeVersion() + "-" + sideName + ".jar");
        List<Path> fileSystemJars;
        try (JarFile jar = new JarFile(installer.toFile())) {
            JsonObject version = json(jar, "version.json");
            fileSystemJars = fileSystemJars(version, directory.resolve("libraries"));
            for (JsonElement element : version.getAsJsonArray("libraries")) {
                JsonObject library = element.getAsJsonObject();
                String name = library.get("name").getAsString();
                if (name.startsWith("net.minecraftforge:forge:")
                        && (name.endsWith(":client") || name.endsWith(":server"))) {
                    // The patched game jar, which Poly Mod Loader builds itself below.
                    continue;
                }
                JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
                String path = artifact.get("path").getAsString();
                String sha1 = artifact.get("sha1").getAsString();
                String url = artifact.has("url") ? artifact.get("url").getAsString() : "";
                Path target = directory.resolve("libraries").resolve(path).normalize();
                if (!target.startsWith(directory.resolve("libraries"))) {
                    throw new IOException("Forge library path " + path + " leaves the libraries folder");
                }
                ZipEntry bundled = jar.getEntry("maven/" + path);
                if (url.isEmpty() && bundled == null) {
                    throw new IOException("Forge's installer names " + name + " but has neither a URL nor a copy");
                }
                ensure(
                        target,
                        sha1,
                        bundled != null
                                ? file -> copy(jar, bundled, file)
                                : file -> downloader.download(URI.create(url), file),
                        log);
                libraries.add(target);
            }

            ZipEntry patches = jar.getEntry("data/" + sideName + ".lzma");
            if (patches == null) {
                throw new IOException("Forge's installer has no " + sideName + " patches");
            }
            Path stamp = directory.resolve(patched.getFileName() + ".input");
            String input = sha1(vanillaGame) + " " + release.installerSha1();
            if (!Files.isRegularFile(patched)
                    || !Files.isRegularFile(stamp)
                    || !Files.readString(stamp).strip().equals(input)) {
                ForgePatchSet patchSet;
                try (InputStream in = jar.getInputStream(patches)) {
                    patchSet = ForgePatchSet.read(in);
                }
                patchSet.patch(
                        vanillaGame,
                        patched,
                        side == GameSide.CLIENT ? Optional.of(".forge_patched_minecraft") : Optional.empty());
                Files.writeString(stamp, input);
                log.accept("Built " + patched.getFileName() + " (" + patchSet.size() + " Forge patches, "
                        + patchSet.newClasses() + " new classes)");
            }
        }
        return new Prepared(release, directory, patched, List.copyOf(libraries), fileSystemJars);
    }

    /**
     * Where the jars Forge's file systems come from ({@link ForgeFileSystems#ARTIFACTS}) are below {@code libraries},
     * by the names and paths in the installer's {@code version.json}, in {@link ForgeFileSystems#ARTIFACTS} order.
     *
     * @throws IOException when {@code version} does not list one of them
     */
    static List<Path> fileSystemJars(JsonObject version, Path libraries) throws IOException {
        List<Path> jars = new ArrayList<>();
        for (String artifact : ForgeFileSystems.ARTIFACTS) {
            Path found = null;
            for (JsonElement element : version.getAsJsonArray("libraries")) {
                JsonObject library = element.getAsJsonObject();
                String[] name = library.get("name").getAsString().split(":");
                if (name.length == 3 && artifact.equals(name[0] + ":" + name[1])) {
                    String path = library.getAsJsonObject("downloads")
                            .getAsJsonObject("artifact")
                            .get("path")
                            .getAsString();
                    found = libraries.resolve(path).normalize();
                }
            }
            if (found == null) {
                throw new IOException("Forge's installer does not list " + artifact
                        + ", whose file system FML needs on the JVM class path");
            }
            jars.add(found);
        }
        return List.copyOf(jars);
    }

    /** The default downloader: HTTPS through the JDK's client, following redirects. */
    static Downloader httpDownloader() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        return (uri, target) -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "Poly Mod Loader")
                    .build();
            try {
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
                if (response.statusCode() != 200) {
                    Files.deleteIfExists(target);
                    throw new IOException("HTTP " + response.statusCode() + " for " + uri);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading " + uri, e);
            }
        };
    }

    /** Writes one file's content to the path it is given. */
    @FunctionalInterface
    interface Fetch {
        void into(Path file) throws IOException;
    }

    /**
     * Keeps {@code target} when it has {@code sha1}; otherwise fetches it to a partial file, checks the hash and only
     * then moves it to {@code target}, so a damaged download never stays there.
     */
    static void ensure(Path target, String sha1, Fetch fetch, Consumer<String> log) throws IOException {
        if (Files.isRegularFile(target) && sha1(target).equalsIgnoreCase(sha1)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling(target.getFileName() + ".partial");
        try {
            Files.deleteIfExists(partial);
            fetch.into(partial);
            String actual = sha1(partial);
            if (!actual.equalsIgnoreCase(sha1)) {
                throw new IOException(target.getFileName() + " has SHA-1 " + actual + ", expected " + sha1
                        + "; the download is damaged or was changed");
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.accept("Downloaded " + target.getFileName());
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    static void copy(JarFile jar, ZipEntry entry, Path target) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static JsonObject json(JarFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null) {
            throw new IOException("Forge's installer has no " + name);
        }
        try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static String sha1(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-1", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
