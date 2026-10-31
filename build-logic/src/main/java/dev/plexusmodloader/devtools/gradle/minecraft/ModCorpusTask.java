package dev.plexusmodloader.devtools.gradle.minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * {@code modCorpus}: downloads a fixed set of real mods from Modrinth for compatibility tests (design doc
 * §15.4). The set is the lock file in the repository (mod, version, file, SHA-512); every download is checked
 * against its hash, and the jars stay in the build directory, never in the repository. With {@link
 * #getRefresh()} the lock is rebuilt first from Modrinth's most downloaded mods for the game version and
 * loader, plus the dependencies those versions require.
 *
 * <p>The loader {@value #QUILT} is chosen differently (Quilt full support Q5, ADR-0027): almost every mod Modrinth
 * lists for Quilt on 26.x is its Fabric jar, which the Fabric corpus already runs. The Quilt corpus is therefore
 * every project with a build for the game version made for Quilt and not for Fabric (its newest such build), in
 * Modrinth's download order, plus the dependencies those builds require (their own Quilt-only build when they have
 * one, otherwise their newest build Modrinth lists for Quilt, which a Quilt player installs too).
 */
@DisableCachingByDefault(because = "Downloads from the network")
public abstract class ModCorpusTask extends DefaultTask {

    /** The loaders of {@code modCorpus}: the most downloaded mods of each. */
    static final List<String> DEFAULT_LOADERS = List.of("fabric", "neoforge", "forge");

    /** The loader whose corpus is every Quilt-only build instead of the most downloaded projects. */
    static final String QUILT = "quilt";

    private static final String API = "https://api.modrinth.com/v2";
    private static final int SEARCH_PAGE = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** The committed lock file. */
    @Internal
    public abstract RegularFileProperty getLockFile();

    /** Where the jars go: {@code <loader>/<file>}. */
    @Internal
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<String> getGameVersion();

    /** Rebuild the lock from Modrinth before downloading. */
    @Input
    public abstract Property<Boolean> getRefresh();

    /** Most downloaded mods per loader when refreshing (not used for {@value #QUILT}, which takes every one). */
    @Input
    public abstract Property<Integer> getTopPerLoader();

    /**
     * The loaders this task's lock file covers, and the only folders of the output it cleans; {@link
     * #DEFAULT_LOADERS} unless set.
     */
    @Input
    public abstract ListProperty<String> getLoaders();

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .sslContext(SystemTrustedTls.context())
            .build();

    public ModCorpusTask() {
        getLoaders().convention(DEFAULT_LOADERS);
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        Path lock = getLockFile().get().getAsFile().toPath();
        if (getRefresh().get() || !Files.exists(lock)) {
            Files.createDirectories(lock.getParent());
            Files.writeString(lock, GSON.toJson(refresh()) + "\n", StandardCharsets.UTF_8);
            getLogger().lifecycle("Wrote {}", lock);
        }
        JsonObject corpus = JsonParser.parseString(Files.readString(lock, StandardCharsets.UTF_8))
                .getAsJsonObject();
        Path output = getOutputDirectory().get().getAsFile().toPath();
        int downloaded = 0;
        int present = 0;
        Set<Path> listed = new HashSet<>();
        for (JsonElement element : corpus.getAsJsonArray("mods")) {
            JsonObject mod = element.getAsJsonObject();
            if (!mod.has("file")) {
                continue;
            }
            Path target = output.resolve(mod.get("loader").getAsString()).resolve(mod.get("file").getAsString());
            listed.add(target);
            String sha512 = mod.get("sha512").getAsString();
            if (Files.exists(target) && sha512(Files.readAllBytes(target)).equals(sha512)) {
                present++;
                continue;
            }
            byte[] data = get(mod.get("url").getAsString());
            if (!sha512(data).equals(sha512)) {
                throw new GradleException(
                        "SHA-512 of " + mod.get("url").getAsString() + " does not match the lock file");
            }
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            downloaded++;
        }
        int removed = removeUnlisted(output, listed, getLoaders().get());
        getLogger()
                .lifecycle(
                        "Mod corpus in {}: {} downloaded, {} already present, {} no longer listed removed",
                        output,
                        downloaded,
                        present,
                        removed);
    }

    /**
     * Deletes the jars in the {@code loaders} folders of {@code output} that are not in {@code listed}: the runs take
     * every jar of a loader folder, so a jar the lock file replaced (another version of the same mod) would otherwise
     * run alongside its replacement. Folders of other loaders belong to another lock file and are left alone.
     */
    static int removeUnlisted(Path output, Set<Path> listed, List<String> loaders) throws IOException {
        int removed = 0;
        for (String loader : loaders) {
            Path folder = output.resolve(loader);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            List<Path> jars;
            try (var files = Files.list(folder)) {
                jars = files.filter(file -> file.getFileName().toString().endsWith(".jar"))
                        .filter(file -> !listed.contains(file))
                        .toList();
            }
            for (Path jar : jars) {
                Files.delete(jar);
                removed++;
            }
        }
        return removed;
    }

    /**
     * The name a downloaded jar gets locally: Modrinth's file name with the characters Windows refuses in file names
     * replaced by {@code _} (a 26.1 Quilt build is called {@code abandoned Quilt 26.*.jar}).
     */
    static String localFileName(String fileName) {
        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    /**
     * The newest of {@code versions} (Modrinth lists them newest first) made for Quilt and not for Fabric, or {@code
     * null}: a build that names Fabric is the Fabric jar, which the Fabric corpus runs.
     */
    static JsonObject quiltOnlyBuild(JsonArray versions) {
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            List<String> loaders = loaders(version);
            if (loaders.contains(QUILT) && !loaders.contains("fabric")) {
                return version;
            }
        }
        return null;
    }

    /** A dependency of the Quilt corpus: its Quilt-only build when it has one, otherwise the newest; {@code null} if none. */
    static JsonObject dependencyBuild(JsonArray versions) {
        JsonObject quiltOnly = quiltOnlyBuild(versions);
        if (quiltOnly != null) {
            return quiltOnly;
        }
        return versions.isEmpty() ? null : versions.get(0).getAsJsonObject();
    }

    private static List<String> loaders(JsonObject version) {
        List<String> loaders = new ArrayList<>();
        if (version.has("loaders")) {
            version.getAsJsonArray("loaders").forEach(loader -> loaders.add(loader.getAsString()));
        }
        return loaders;
    }

    private JsonObject refresh() throws IOException, InterruptedException {
        String game = getGameVersion().get();
        JsonArray mods = new JsonArray();
        Set<String> seen = new HashSet<>();
        boolean quiltOnly = false;
        for (String loader : getLoaders().get()) {
            Deque<String[]> queue = new ArrayDeque<>();
            String facets = "[[\"versions:" + game + "\"],[\"categories:" + loader + "\"],[\"project_type:mod\"]]";
            if (loader.equals(QUILT)) {
                quiltOnly = true;
                for (int offset = 0; ; offset += SEARCH_PAGE) {
                    JsonArray hits = search(facets, SEARCH_PAGE, offset);
                    hits.forEach(hit -> queue.add(
                            new String[] {hit.getAsJsonObject().get("project_id").getAsString(), "quilt-only"}));
                    if (hits.size() < SEARCH_PAGE) {
                        break;
                    }
                }
                getLogger().lifecycle("{} projects list Quilt for {}; looking for Quilt-only builds", queue.size(), game);
            } else {
                search(facets, getTopPerLoader().get(), 0)
                        .forEach(hit -> queue.add(
                                new String[] {hit.getAsJsonObject().get("project_id").getAsString(), "top"}));
            }
            while (!queue.isEmpty()) {
                String[] next = queue.removeFirst();
                if (!seen.add(loader + "/" + next[0])) {
                    continue;
                }
                JsonArray versions = api("/project/" + next[0] + "/version?loaders=" + encode("[\"" + loader + "\"]")
                                + "&game_versions=" + encode("[\"" + game + "\"]"))
                        .getAsJsonArray();
                JsonObject version;
                if (!loader.equals(QUILT)) {
                    version = versions.isEmpty() ? null : versions.get(0).getAsJsonObject();
                } else if (next[1].equals("quilt-only")) {
                    version = quiltOnlyBuild(versions);
                    if (version == null) {
                        // Only Fabric jars for Quilt: the Fabric corpus's business.
                        seen.remove(loader + "/" + next[0]);
                        continue;
                    }
                } else {
                    version = dependencyBuild(versions);
                    if (version == null) {
                        // A Quilt mod may require a mod Modrinth lists for Fabric only; Quilt Loader loads Fabric mods.
                        version = dependencyBuild(
                                api("/project/" + next[0] + "/version?loaders=" + encode("[\"fabric\"]")
                                                + "&game_versions=" + encode("[\"" + game + "\"]"))
                                        .getAsJsonArray());
                    }
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("loader", loader);
                entry.addProperty("projectId", next[0]);
                entry.addProperty("reason", next[1]);
                if (version == null) {
                    entry.addProperty("missing", true);
                    mods.add(entry);
                    continue;
                }
                JsonObject file = version.getAsJsonArray("files").get(0).getAsJsonObject();
                for (JsonElement candidate : version.getAsJsonArray("files")) {
                    if (candidate.getAsJsonObject().get("primary").getAsBoolean()) {
                        file = candidate.getAsJsonObject();
                    }
                }
                entry.addProperty("version", version.get("version_number").getAsString());
                entry.addProperty("versionId", version.get("id").getAsString());
                entry.addProperty("file", localFileName(file.get("filename").getAsString()));
                entry.addProperty("url", file.get("url").getAsString());
                entry.addProperty("sha512", file.getAsJsonObject("hashes").get("sha512").getAsString());
                mods.add(entry);
                for (JsonElement dependency : version.getAsJsonArray("dependencies")) {
                    JsonObject declared = dependency.getAsJsonObject();
                    if (declared.get("dependency_type").getAsString().equals("required")
                            && declared.has("project_id")
                            && !declared.get("project_id").isJsonNull()) {
                        queue.add(new String[] {declared.get("project_id").getAsString(), "required by " + next[0]});
                    }
                }
            }
        }
        JsonObject corpus = new JsonObject();
        corpus.addProperty("gameVersion", game);
        corpus.addProperty("created", LocalDate.now().toString());
        corpus.addProperty(
                "source",
                quiltOnly
                        ? "Modrinth: every mod with a build for " + game
                                + " made for Quilt and not for Fabric (its newest such build), in download order,"
                                + " plus the dependencies those builds require"
                        : "Modrinth: the " + getTopPerLoader().get() + " most downloaded mods per loader for " + game
                                + ", plus the dependencies their versions require");
        corpus.add("mods", mods);
        return corpus;
    }

    private JsonArray search(String facets, int limit, int offset) throws IOException, InterruptedException {
        return api("/search?limit=" + limit + "&offset=" + offset + "&index=downloads&facets=" + encode(facets))
                .getAsJsonObject()
                .getAsJsonArray("hits");
    }

    private JsonElement api(String path) throws IOException, InterruptedException {
        return JsonParser.parseString(new String(get(API + path), StandardCharsets.UTF_8));
    }

    private byte[] get(String url) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            long wait = 2000L * (attempt + 1);
            try {
                HttpResponse<byte[]> response = http.send(
                        HttpRequest.newBuilder(URI.create(url))
                                .header("User-Agent", "Plexus Mod Loader compatibility tests (modCorpus)")
                                .timeout(Duration.ofMinutes(2))
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                if (response.statusCode() == 429) {
                    // Modrinth's rate limit (300 requests a minute): wait until the window resets.
                    wait = 1000L
                            * (1
                                    + response.headers()
                                            .firstValue("X-Ratelimit-Reset")
                                            .map(Long::parseLong)
                                            .orElse(60L));
                }
                last = new IOException("HTTP " + response.statusCode() + " for " + url);
            } catch (IOException e) {
                last = e;
            }
            Thread.sleep(wait);
        }
        throw last;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String sha512(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is required by every Java runtime", e);
        }
    }
}
