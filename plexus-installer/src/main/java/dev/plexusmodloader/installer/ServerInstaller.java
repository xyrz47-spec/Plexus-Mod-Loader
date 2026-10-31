package dev.plexusmodloader.installer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Installs a Plexus Mod Loader dedicated server into a folder:
 *
 * <ol>
 *   <li>Mojang's server jar for the Minecraft version, found through Mojang's version manifest and checked by
 *       SHA-1. Mojang ships it as a bundler; the server jar and libraries inside go to {@code versions/} and
 *       {@code libraries/}, each checked by the SHA-256 the bundler lists, as the bundler itself would do
 *   <li>the Plexus Mod Loader jars into {@code libraries/} (checked by SHA-1)
 *   <li>{@value #LAUNCH_JAR}, which starts Plexus Mod Loader's server main class with all of them on its manifest class
 *       path, so {@code java -jar plexusmodloader-server.jar nogui} works as on any server host
 *   <li>{@code run.bat} and {@code run.sh} with the memory setting, and {@code mods/}
 * </ol>
 *
 * <p>It does not accept Minecraft's EULA. The first start stops at the EULA check, and the server's owner accepts it
 * in {@code eula.txt}. Installing again repairs or updates the same folder; worlds, settings and mods stay.
 */
final class ServerInstaller {

    static final String LAUNCH_JAR = "plexusmodloader-server.jar";
    /** In the bootstrap jar of the payload; defaults the launch target to {@code dedicated_server}. */
    static final String SERVER_MAIN_CLASS = "dev.plexusmodloader.bootstrap.PlexusServerMain";

    static final URI VERSION_MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    /** Fetches a URL to a file; tests answer from local files. */
    @FunctionalInterface
    interface Downloader {
        void download(URI uri, Path target) throws IOException;
    }

    private final InstallerPayload payload;
    private final Downloader downloader;

    ServerInstaller(InstallerPayload payload, Downloader downloader) {
        this.payload = payload;
        this.downloader = downloader;
    }

    /**
     * Installs the server for {@code minecraftVersion} into {@code directory}, creating the folder when it is missing.
     *
     * @param memory the server's {@code -Xms}/{@code -Xmx}, written to the start scripts; absent sizes are left to
     *     Java
     * @param progress receives one line per step, for the window or the console
     * @return warnings the user should see; empty when all went as expected
     * @throws InstallerException when the version is not supported or a download does not match its checksum
     * @throws IOException when a download fails or a file cannot be written
     */
    List<String> install(Path directory, String minecraftVersion, JavaMemory memory, Consumer<String> progress)
            throws InstallerException, IOException {
        if (!payload.minecraftVersions().contains(minecraftVersion)) {
            throw new InstallerException("Plexus Mod Loader " + payload.plexusVersion() + " does not support Minecraft "
                    + minecraftVersion + "; it supports " + String.join(", ", payload.minecraftVersions()) + ".");
        }
        if (Files.exists(directory) && !Files.isDirectory(directory)) {
            throw new InstallerException(directory + " is a file, not a folder. Choose a folder for the server.");
        }
        Path root = Files.createDirectories(directory).toAbsolutePath().normalize();
        List<String> warnings = new ArrayList<>();
        String memoryWarning = memory.warning(JavaMemory.physicalMegabytes());
        if (memoryWarning != null) {
            warnings.add(memoryWarning);
        }

        JsonObject version = versionJson(root, minecraftVersion, progress);
        JsonObject server = object(object(version, "downloads"), "server");
        Path bundler = root.resolve("versions")
                .resolve(minecraftVersion)
                .resolve("server-bundler-" + minecraftVersion + ".jar");
        fetch(URI.create(string(server, "url")), string(server, "sha1"), bundler, progress);

        List<String> classPath = new ArrayList<>();
        Path libraries = root.resolve("libraries");
        for (InstallerPayload.Library library : payload.libraries()) {
            LauncherProfileInstaller.installLibrary(payload, libraries, library);
            classPath.add("libraries/" + library.path());
        }
        progress.accept(payload.libraries().size() + " Plexus Mod Loader libraries are in " + libraries);
        String serverJar;
        try (JarFile jar = new JarFile(bundler.toFile())) {
            for (String library : unbundle(jar, "META-INF/libraries.list", root.resolve("libraries"))) {
                classPath.add("libraries/" + library);
            }
            List<String> servers = unbundle(jar, "META-INF/versions.list", root.resolve("versions"));
            if (servers.size() != 1) {
                throw new InstallerException("Mojang's server jar for " + minecraftVersion + " holds " + servers.size()
                        + " game jars instead of one; Plexus Mod Loader cannot use it.");
            }
            serverJar = "versions/" + servers.get(0);
        }
        classPath.add(serverJar);
        progress.accept("Minecraft " + minecraftVersion + " server is in " + root.resolve(serverJar));

        writeLaunchJar(root.resolve(LAUNCH_JAR), classPath);
        int java = version.has("javaVersion")
                ? object(version, "javaVersion").get("majorVersion").getAsInt()
                : Runtime.version().feature();
        writeScripts(root, minecraftVersion, java, memory);
        Files.createDirectories(root.resolve("mods"));
        progress.accept("Wrote " + LAUNCH_JAR + ", run.bat and run.sh; put mods into " + root.resolve("mods"));
        if (!Files.isRegularFile(root.resolve("eula.txt"))) {
            progress.accept("The first start stops at Minecraft's EULA: read https://aka.ms/MinecraftEULA and set"
                    + " eula=true in eula.txt to accept it.");
        }
        return warnings;
    }

    /** The version JSON of {@code minecraftVersion}, from Mojang's manifest; both are kept next to the server. */
    private JsonObject versionJson(Path root, String minecraftVersion, Consumer<String> progress)
            throws InstallerException, IOException {
        Path versions = Files.createDirectories(root.resolve("versions"));
        Path manifestFile = versions.resolve("version_manifest_v2.json");
        Path partial = Files.createTempFile(versions, "version_manifest_v2.json", ".partial");
        try {
            downloader.download(VERSION_MANIFEST, partial);
            LauncherProfileInstaller.move(partial, manifestFile);
        } finally {
            Files.deleteIfExists(partial);
        }
        JsonObject entry = null;
        for (JsonElement element : parse(manifestFile).getAsJsonArray("versions")) {
            if (minecraftVersion.equals(string(element.getAsJsonObject(), "id"))) {
                entry = element.getAsJsonObject();
            }
        }
        if (entry == null) {
            throw new InstallerException("Mojang's version list has no Minecraft " + minecraftVersion + ".");
        }
        Path versionFile = versions.resolve(minecraftVersion).resolve(minecraftVersion + ".json");
        fetch(URI.create(string(entry, "url")), string(entry, "sha1"), versionFile, progress);
        return parse(versionFile);
    }

    /** Keeps {@code target} when it has {@code sha1}; otherwise downloads it to a partial file and checks it. */
    private void fetch(URI uri, String sha1, Path target, Consumer<String> progress)
            throws InstallerException, IOException {
        if (Files.isRegularFile(target) && digest(target, "SHA-1").equalsIgnoreCase(sha1)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Path partial =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".partial");
        try {
            downloader.download(uri, partial);
            String actual = digest(partial, "SHA-1");
            if (!actual.equalsIgnoreCase(sha1)) {
                throw new InstallerException("The download of " + target.getFileName() + " is damaged (SHA-1 " + actual
                        + ", expected " + sha1 + "). Try again.");
            }
            LauncherProfileInstaller.move(partial, target);
            progress.accept("Downloaded " + target.getFileName());
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    /**
     * Extracts the files a bundler list names ({@code <sha256>\t<id>\t<path>} per line) below {@code target},
     * each checked by its SHA-256, and returns their paths relative to {@code target}.
     */
    private static List<String> unbundle(JarFile bundler, String list, Path target)
            throws InstallerException, IOException {
        ZipEntry index = bundler.getEntry(list);
        if (index == null) {
            throw new InstallerException("Mojang's server jar has no " + list + "; Plexus Mod Loader cannot use it.");
        }
        String folder = list.substring(0, list.lastIndexOf('.')) + "/";
        List<String> paths = new ArrayList<>();
        String text;
        try (InputStream input = bundler.getInputStream(index)) {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.strip().split("\t");
            if (columns.length != 3) {
                throw new InstallerException("Mojang's server jar has an unexpected line in " + list + ": " + line);
            }
            String sha256 = columns[0];
            String path = columns[2];
            Path file = target.resolve(path).normalize();
            if (!file.startsWith(target)) {
                throw new InstallerException("Mojang's server jar names " + path + ", which leaves " + target);
            }
            paths.add(path);
            if (Files.isRegularFile(file) && digest(file, "SHA-256").equalsIgnoreCase(sha256)) {
                continue;
            }
            ZipEntry entry = bundler.getEntry(folder + path);
            if (entry == null) {
                throw new InstallerException("Mojang's server jar lists " + path + " but does not contain it.");
            }
            Files.createDirectories(file.getParent());
            Path partial =
                    Files.createTempFile(file.getParent(), file.getFileName().toString(), ".partial");
            try {
                try (InputStream input = bundler.getInputStream(entry)) {
                    Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING);
                }
                String actual = digest(partial, "SHA-256");
                if (!actual.equalsIgnoreCase(sha256)) {
                    throw new InstallerException("Mojang's server jar holds a damaged " + path + " (SHA-256 " + actual
                            + ", expected " + sha256 + "). Try again.");
                }
                LauncherProfileInstaller.move(partial, file);
            } finally {
                Files.deleteIfExists(partial);
            }
        }
        return paths;
    }

    /** A jar with only a manifest: Plexus Mod Loader's server main class and every jar it needs, relative to the folder. */
    private void writeLaunchJar(Path jar, List<String> classPath) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, SERVER_MAIN_CLASS);
        attributes.put(Attributes.Name.CLASS_PATH, String.join(" ", classPath));
        // java -jar starts Plexus Mod Loader's agent before the main class, loaded from the Class-Path (ADR-0024).
        payload.agent()
                .ifPresent(agent -> attributes.put(new Attributes.Name("Launcher-Agent-Class"), agent.agentClass()));
        attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, "Plexus Mod Loader server");
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, payload.plexusVersion());
        // Minecraft's server libraries (JNA) call native code; Java 22 and newer warn about that without this.
        attributes.put(new Attributes.Name("Enable-Native-Access"), "ALL-UNNAMED");
        Path partial = Files.createTempFile(jar.getParent(), jar.getFileName().toString(), ".partial");
        try {
            try (OutputStream file = Files.newOutputStream(partial);
                    JarOutputStream out = new JarOutputStream(file, manifest)) {
                // A manifest is all this jar holds.
                out.flush();
            }
            LauncherProfileInstaller.move(partial, jar);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private void writeScripts(Path root, String minecraftVersion, int java, JavaMemory memory) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.addAll(memory.arguments());
        // The server bundler's own launcher passes no flags; this one silences JDK warnings about sun.misc.Unsafe.
        command.add("--sun-misc-unsafe-memory-access=allow");
        command.add("-jar");
        command.add(LAUNCH_JAR);
        command.add("nogui");
        String about = "Plexus Mod Loader " + payload.plexusVersion() + " server for Minecraft " + minecraftVersion
                + ". Needs Java " + java + " or newer.";
        LauncherProfileInstaller.writeAtomically(
                root.resolve("run.bat"),
                ("@echo off\r\nrem " + about + "\r\ncd /d \"%~dp0\"\r\n" + String.join(" ", command)
                                + " %*\r\npause\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        Path shell = root.resolve("run.sh");
        LauncherProfileInstaller.writeAtomically(
                shell,
                ("#!/usr/bin/env sh\n# " + about + "\ncd \"$(dirname \"$0\")\"\nexec " + String.join(" ", command)
                                + " \"$@\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        if (Files.getFileStore(shell).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    shell,
                    EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE));
        }
    }

    /** Downloads over HTTPS with Java's own client, following redirects. */
    static Downloader httpDownloader() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        return (uri, target) -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "PlexusModLoader-Installer")
                    .build();
            HttpResponse<Path> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download of " + uri + " was interrupted", e);
            }
            if (response.statusCode() != 200) {
                throw new IOException("Download of " + uri + " failed with HTTP " + response.statusCode());
            }
        };
    }

    private static JsonObject parse(Path file) throws InstallerException, IOException {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new InstallerException("Mojang's " + file.getFileName() + " could not be read: " + e.getMessage());
        }
    }

    private static JsonObject object(JsonObject parent, String key) throws InstallerException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new InstallerException(
                    "Mojang's version data has no \"" + key + "\"; Plexus Mod Loader cannot use it.");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonObject parent, String key) throws InstallerException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new InstallerException(
                    "Mojang's version data has no \"" + key + "\"; Plexus Mod Loader cannot use it.");
        }
        return value.getAsString();
    }

    static String digest(Path file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has " + algorithm, e);
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
}
