package dev.polymodloader.devtools.gradle.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.gradle.api.logging.Logger;

/**
 * Installs a Minecraft client or dedicated server distribution into a shared cache, following
 * Mojang's version manifest (library rules, JVM arguments, assets). All downloads are hash-verified.
 * The Minecraft jars are downloaded from Mojang at build time and are never redistributed by Poly Mod Loader.
 * Files copied by hand into a {@link LocalMinecraftFiles} directory are verified the same way and
 * copied into the cache instead of being downloaded.
 */
final class MinecraftDistributionInstaller {

    private static final URI VERSION_MANIFEST =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final URI ASSET_OBJECTS = URI.create("https://resources.download.minecraft.net/");
    static final String DEV_PLAYER_NAME = "PolyDev";

    private final VerifiedDownloader downloader = new VerifiedDownloader();
    private final Path cacheRoot;
    private final LocalMinecraftFiles localFiles;
    private final Logger logger;

    MinecraftDistributionInstaller(Path cacheRoot, LocalMinecraftFiles localFiles, Logger logger) {
        this.cacheRoot = cacheRoot;
        this.localFiles = localFiles;
        this.logger = logger;
    }

    /**
     * A version JSON, the client jar it names and, for an obfuscated version (before 26.1), Mojang's client
     * mappings ({@code downloads.client_mappings}, ADR-0019).
     */
    record ClientFiles(JsonObject versionJson, Path clientJar, Optional<Path> clientMappings) {}

    /**
     * Downloads and verifies only the version JSON, the client jar and the client mappings if the version has
     * them, without libraries or assets ({@code minecraftVersionDiff}). Uses the same cache files as {@link
     * #installClient}. The mappings stay in the cache on this machine (ADR-0019 point 3).
     */
    ClientFiles clientFiles(String gameVersion) {
        JsonObject version = versionJson(gameVersion);
        Path versionDir = cacheRoot.resolve("versions").resolve(gameVersion);
        JsonObject clientDownload = download(version, "client", gameVersion);
        Path clientJar = versionDir.resolve("client-" + gameVersion + ".jar");
        localFiles.seedClientJar(gameVersion, string(clientDownload, "sha1"), clientJar);
        downloader.downloadSha1Verified(uri(clientDownload, "url"), string(clientDownload, "sha1"), clientJar);
        JsonObject downloads = version.getAsJsonObject("downloads");
        if (!downloads.has("client_mappings")) {
            return new ClientFiles(version, clientJar, Optional.empty());
        }
        JsonObject mappingsDownload = downloads.getAsJsonObject("client_mappings");
        Path mappings = versionDir.resolve("client-mappings-" + gameVersion + ".txt");
        localFiles.seedClientMappings(gameVersion, string(mappingsDownload, "sha1"), mappings);
        downloader.downloadSha1Verified(uri(mappingsDownload, "url"), string(mappingsDownload, "sha1"), mappings);
        return new ClientFiles(version, clientJar, Optional.of(mappings));
    }

    MinecraftLaunchSpec installClient(String gameVersion) {
        JsonObject version = versionJson(gameVersion);
        Path versionDir = cacheRoot.resolve("versions").resolve(gameVersion);
        checkClientLaunchFormat(version, gameVersion);

        ClientFiles files = clientFiles(gameVersion);
        List<Path> libraryPaths = clientLibraries(version, gameVersion);
        // An obfuscated version runs, and its modules compile, on the jar remapped to Mojang names (ADR-0019).
        Path clientJar = files.clientMappings()
                .map(mappings -> RemappedGameJar.ensure(
                                files.clientJar(),
                                mappings,
                                libraryPaths,
                                versionDir.resolve("client-" + gameVersion + "-mojang.jar"),
                                logger)
                        .jar())
                .orElse(files.clientJar());

        List<String> libraries = libraryPaths.stream().map(Path::toString).toList();

        Path assetsDir = cacheRoot.resolve("assets");
        String assetIndexId = installAssets(version.getAsJsonObject("assetIndex"), assetsDir);

        Path nativesDir = versionDir.resolve("natives");
        Map<String, String> jvmVariables = Map.of(
                "natives_directory", nativesDir.toString(),
                "launcher_name", "poly-dev",
                "launcher_version", gameVersion,
                "classpath", "");
        List<String> jvmArguments = new ArrayList<>();
        JsonArray jvmTemplate = version.getAsJsonObject("arguments").getAsJsonArray("jvm");
        List<String> resolvedJvm = LibraryRules.resolveArguments(jvmTemplate, jvmVariables);
        for (int i = 0; i < resolvedJvm.size(); i++) {
            if (resolvedJvm.get(i).equals("-cp")) {
                i++; // class path is supplied by the run task
                continue;
            }
            jvmArguments.add(resolvedJvm.get(i));
        }

        String offlineUuid = UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + DEV_PLAYER_NAME).getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        Map<String, String> gameVariables = Map.of(
                "auth_player_name", DEV_PLAYER_NAME,
                "version_name", gameVersion,
                "game_directory", MinecraftLaunchSpec.GAME_DIRECTORY_PLACEHOLDER,
                "assets_root", assetsDir.toString(),
                "assets_index_name", assetIndexId,
                "auth_uuid", offlineUuid,
                "auth_access_token", "0",
                "version_type", string(version, "type"));
        List<String> gameArguments = LibraryRules.resolveOptionArguments(
                version.getAsJsonObject("arguments").getAsJsonArray("game"), gameVariables);

        return new MinecraftLaunchSpec(
                gameVersion, "client", clientJar.toString(), libraries, jvmArguments, gameArguments);
    }

    /**
     * Downloads and verifies the client libraries this OS uses (the {@code downloads.artifact} of each library
     * whose rules allow it), without assets or natives. Used by {@link #installClient} and by {@code
     * remapMinecraft}, which loads the remapped jar with them.
     */
    List<Path> clientLibraries(String gameVersion) {
        return clientLibraries(versionJson(gameVersion), gameVersion);
    }

    private List<Path> clientLibraries(JsonObject version, String gameVersion) {
        List<Path> libraries = new ArrayList<>();
        for (JsonElement element : version.getAsJsonArray("libraries")) {
            JsonObject library = element.getAsJsonObject();
            if (!LibraryRules.allows(library.getAsJsonArray("rules"))) {
                continue;
            }
            JsonObject downloads = library.getAsJsonObject("downloads");
            JsonObject artifact = downloads == null ? null : downloads.getAsJsonObject("artifact");
            if (artifact == null) {
                throw new IllegalStateException("Library " + library.get("name") + " in the version JSON of Minecraft "
                        + gameVersion + " has no downloads.artifact (natives-only entries of versions before"
                        + " 1.19 are not supported).");
            }
            Path target = cacheRoot.resolve("libraries").resolve(string(artifact, "path"));
            localFiles.seedLibrary(gameVersion, string(artifact, "path"), string(artifact, "sha1"), target);
            downloader.downloadSha1Verified(uri(artifact, "url"), string(artifact, "sha1"), target);
            libraries.add(target);
        }
        return libraries;
    }

    /**
     * Installs the dedicated server. Mojang ships it as a "bundler" jar that contains the real
     * server jar and its libraries; Poly Mod Loader extracts them so the server jar can be loaded through Poly Mod Loader's
     * game class loader instead of the bundler's own launcher.
     */
    MinecraftLaunchSpec installServer(String gameVersion) {
        ServerFiles files = serverFiles(gameVersion);
        Path versionDir = cacheRoot.resolve("versions").resolve(gameVersion);
        // An obfuscated version runs on the server jar remapped to Mojang names (ADR-0019).
        Path serverJar = files.serverMappings()
                .map(mappings -> RemappedGameJar.ensure(
                                files.serverJar(),
                                mappings,
                                files.libraries(),
                                versionDir.resolve("server-" + gameVersion + "-mojang.jar"),
                                logger)
                        .jar())
                .orElse(files.serverJar());
        // The bundler's own launcher passes no JVM flags; these match the client manifest and
        // silence JDK 25 warnings from JNA (native access) and JOML (sun.misc.Unsafe).
        List<String> jvmArguments =
                List.of("--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED");
        return new MinecraftLaunchSpec(
                gameVersion,
                "server",
                serverJar.toString(),
                files.libraries().stream().map(Path::toString).toList(),
                jvmArguments,
                List.of("--nogui"));
    }

    /**
     * The dedicated server jar taken out of Mojang's bundler, the libraries next to it and, for an obfuscated
     * version, Mojang's server mappings ({@code downloads.server_mappings}, ADR-0019).
     */
    record ServerFiles(Path serverJar, List<Path> libraries, Optional<Path> serverMappings) {}

    /** Downloads and verifies the server bundler and extracts it, without remapping anything. */
    ServerFiles serverFiles(String gameVersion) {
        JsonObject version = versionJson(gameVersion);
        Path versionDir = cacheRoot.resolve("versions").resolve(gameVersion);
        Optional<Path> mappings = Optional.empty();
        JsonObject downloads = version.getAsJsonObject("downloads");
        if (downloads != null && downloads.has("server_mappings")) {
            JsonObject mappingsDownload = downloads.getAsJsonObject("server_mappings");
            Path file = versionDir.resolve("server-mappings-" + gameVersion + ".txt");
            localFiles.seedServerMappings(gameVersion, string(mappingsDownload, "sha1"), file);
            downloader.downloadSha1Verified(uri(mappingsDownload, "url"), string(mappingsDownload, "sha1"), file);
            mappings = Optional.of(file);
        }
        JsonObject serverDownload = download(version, "server", gameVersion);
        Path bundlerJar = versionDir.resolve("server-bundler-" + gameVersion + ".jar");
        localFiles.seedServerBundler(gameVersion, string(serverDownload, "sha1"), bundlerJar);
        downloader.downloadSha1Verified(uri(serverDownload, "url"), string(serverDownload, "sha1"), bundlerJar);

        Path extractedDir = versionDir.resolve("server");
        try (JarFile bundler = new JarFile(bundlerJar.toFile())) {
            List<BundledFile> serverJars = readBundleList(bundler, "META-INF/versions.list", "META-INF/versions/");
            List<BundledFile> libraries = readBundleList(bundler, "META-INF/libraries.list", "META-INF/libraries/");
            if (serverJars.size() != 1) {
                throw new IllegalStateException("Expected exactly one server jar in " + bundlerJar + " but found "
                        + serverJars.size());
            }
            Path serverJar = extract(bundler, serverJars.getFirst(), extractedDir.resolve("versions"));
            List<Path> libraryPaths = new ArrayList<>();
            for (BundledFile library : libraries) {
                libraryPaths.add(extract(bundler, library, extractedDir.resolve("libraries")));
            }
            return new ServerFiles(serverJar, List.copyOf(libraryPaths), mappings);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract server bundler " + bundlerJar, e);
        }
    }

    private JsonObject versionJson(String gameVersion) {
        Path versionFile =
                cacheRoot.resolve("versions").resolve(gameVersion).resolve(gameVersion + ".json");
        Optional<Path> localJson = localFiles.versionJson(gameVersion);
        if (localJson.isPresent()) {
            checkLocalVersionJson(localJson.get(), gameVersion);
            try {
                if (!Files.isRegularFile(versionFile) || Files.mismatch(localJson.get(), versionFile) != -1) {
                    LocalMinecraftFiles.copy(localJson.get(), versionFile);
                    logger.lifecycle("Using local Minecraft version JSON {}", localJson.get());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else if (!Files.isRegularFile(versionFile)) {
            JsonObject manifest =
                    JsonParser.parseString(downloader.fetchString(VERSION_MANIFEST)).getAsJsonObject();
            JsonObject entry = null;
            for (JsonElement element : manifest.getAsJsonArray("versions")) {
                if (string(element.getAsJsonObject(), "id").equals(gameVersion)) {
                    entry = element.getAsJsonObject();
                }
            }
            if (entry == null) {
                throw new IllegalStateException("Minecraft version " + gameVersion + " is not in " + VERSION_MANIFEST);
            }
            downloader.downloadSha1Verified(uri(entry, "url"), string(entry, "sha1"), versionFile);
        }
        try {
            return JsonParser.parseString(Files.readString(versionFile)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Rejects a local version JSON that is not the vanilla JSON of {@code gameVersion}. */
    static void checkLocalVersionJson(Path file, String gameVersion) {
        JsonObject json;
        try {
            json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalStateException(
                    "Local Minecraft version JSON " + file + " is not a JSON object: " + e.getMessage(), e);
        }
        JsonElement id = json.get("id");
        if (id == null || !id.getAsString().equals(gameVersion)) {
            throw new IllegalStateException("Local Minecraft version JSON " + file + " has id " + id + ", expected \""
                    + gameVersion + "\". Copy .minecraft/versions/" + gameVersion + "/ as is.");
        }
        if (json.has("inheritsFrom")) {
            throw new IllegalStateException("Local Minecraft version JSON " + file + " inherits from "
                    + json.get("inheritsFrom") + " (a modded launcher profile). Copy the vanilla version folder"
                    + " .minecraft/versions/" + gameVersion + "/ instead.");
        }
    }

    /** Rejects version JSONs from before 1.13, which use {@code minecraftArguments} instead of {@code arguments}. */
    static void checkClientLaunchFormat(JsonObject version, String gameVersion) {
        if (!version.has("arguments")) {
            throw new IllegalStateException("The version JSON of Minecraft " + gameVersion + " has no 'arguments'"
                    + (version.has("minecraftArguments") ? " (it uses the pre-1.13 'minecraftArguments' format)" : "")
                    + "; Poly Mod Loader's client launcher supports only the 'arguments' format of 1.13 and later.");
        }
    }

    private static JsonObject download(JsonObject version, String side, String gameVersion) {
        JsonObject downloads = version.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has(side)) {
            throw new IllegalStateException("The version JSON of Minecraft " + gameVersion + " has no downloads."
                    + side + " entry, so the " + side + " jar cannot be verified or downloaded.");
        }
        return downloads.getAsJsonObject(side);
    }

    private String installAssets(JsonObject assetIndex, Path assetsDir) {
        String indexId = string(assetIndex, "id");
        Path indexFile = assetsDir.resolve("indexes").resolve(indexId + ".json");
        downloader.downloadSha1Verified(uri(assetIndex, "url"), string(assetIndex, "sha1"), indexFile);
        JsonObject objects;
        try {
            objects = JsonParser.parseString(Files.readString(indexFile))
                    .getAsJsonObject()
                    .getAsJsonObject("objects");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        logger.lifecycle("Verifying {} Minecraft asset objects in {}", objects.size(), assetsDir);
        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            List<Future<?>> downloads = new ArrayList<>();
            for (Map.Entry<String, JsonElement> object : objects.entrySet()) {
                JsonObject asset = object.getValue().getAsJsonObject();
                String hash = string(asset, "hash");
                long size = asset.get("size").getAsLong();
                String relative = hash.substring(0, 2) + "/" + hash;
                downloads.add(executor.submit(() -> downloader.downloadAssetObject(
                        ASSET_OBJECTS.resolve(relative), hash, size, assetsDir.resolve("objects").resolve(relative))));
            }
            for (Future<?> download : downloads) {
                download.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading assets", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Asset download failed", e.getCause());
        }
        return indexId;
    }

    private record BundledFile(String sha256, String path) {}

    private static List<BundledFile> readBundleList(JarFile bundler, String listName, String prefix)
            throws IOException {
        ZipEntry entry = bundler.getEntry(listName);
        if (entry == null) {
            throw new IllegalStateException("Server jar is missing " + listName + "; it is not a server bundler"
                    + " (Minecraft versions before 1.18 ship a plain server jar, which is not supported)");
        }
        List<BundledFile> files = new ArrayList<>();
        try (InputStream input = bundler.getInputStream(entry)) {
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.strip().split("\t");
                files.add(new BundledFile(columns[0], prefix + columns[2]));
            }
        }
        return files;
    }

    private static Path extract(JarFile bundler, BundledFile file, Path targetRoot) throws IOException {
        String relative = file.path().substring(file.path().indexOf('/', "META-INF/".length()) + 1);
        Path target = targetRoot.resolve(relative);
        if (Files.isRegularFile(target)
                && file.sha256().equalsIgnoreCase(VerifiedDownloader.hash(target, "SHA-256"))) {
            return target;
        }
        ZipEntry entry = bundler.getEntry(file.path());
        if (entry == null) {
            throw new IllegalStateException("Server bundler is missing " + file.path());
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = bundler.getInputStream(entry)) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = VerifiedDownloader.hash(target, "SHA-256");
        if (!file.sha256().equalsIgnoreCase(actual)) {
            throw new IllegalStateException(
                    "SHA-256 mismatch for bundled " + file.path() + ": expected " + file.sha256() + " got " + actual);
        }
        return target;
    }

    private static String string(JsonObject object, String key) {
        return object.get(key).getAsString();
    }

    private static URI uri(JsonObject object, String key) {
        return URI.create(string(object, key));
    }

    static String currentOsName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac")) {
            return "osx";
        }
        return "linux";
    }
}
