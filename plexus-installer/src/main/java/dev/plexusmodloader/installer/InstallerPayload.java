package dev.plexusmodloader.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the installer installs: the Plexus Mod Loader version, the Minecraft versions it supports, the launcher main
 * class and the library jars. The build writes the index to {@value #INDEX} and the jars below
 * {@value #LIBRARY_ROOT} inside the installer jar.
 *
 * @param plexusVersion Plexus Mod Loader version, such as {@code 0.1.0-alpha.1}
 * @param minecraftVersions Minecraft versions this Plexus Mod Loader supports, newest last
 * @param mainClass the JVM main class of a Plexus Mod Loader game launch
 * @param libraries jars to put into the launcher's {@code libraries/} folder
 * @param agent Plexus Mod Loader's Java agent among those jars (ADR-0024); empty in an index written before it existed
 * @param source where the library jars are read from
 */
record InstallerPayload(
        String plexusVersion,
        List<String> minecraftVersions,
        String mainClass,
        List<Library> libraries,
        Optional<Agent> agent,
        LibrarySource source) {

    static final String INDEX = "META-INF/plexusmodloader/installer.json";
    static final String LIBRARY_ROOT = "META-INF/plexusmodloader/libraries/";

    InstallerPayload {
        minecraftVersions = List.copyOf(minecraftVersions);
        libraries = List.copyOf(libraries);
        if (minecraftVersions.isEmpty()) {
            throw new IllegalArgumentException("The installer lists no Minecraft version");
        }
        List<Library> all = libraries;
        agent.ifPresent(present -> {
            if (all.stream().noneMatch(library -> library.name().equals(present.library()))) {
                throw new IllegalArgumentException(
                        "The installer's agent library " + present.library() + " is not one of its libraries");
            }
        });
    }

    /** A payload without Plexus Mod Loader's Java agent. */
    InstallerPayload(
            String plexusVersion,
            List<String> minecraftVersions,
            String mainClass,
            List<Library> libraries,
            LibrarySource source) {
        this(plexusVersion, minecraftVersions, mainClass, libraries, Optional.empty(), source);
    }

    /**
     * Plexus Mod Loader's Java agent (ADR-0024): the launcher profile starts it with {@code -javaagent:<its jar>}, the
     * server's launch jar names it as {@code Launcher-Agent-Class}.
     *
     * @param library Maven coordinates of the library that holds it (its manifest names it as {@code Premain-Class})
     * @param agentClass the agent class
     */
    record Agent(String library, String agentClass) {}

    /**
     * One library jar.
     *
     * @param name Maven coordinates {@code group:artifact:version}
     * @param path its path below the launcher's {@code libraries/} folder
     * @param sha1 SHA-1 of the jar, hexadecimal
     * @param size size of the jar in bytes
     */
    record Library(String name, String path, String sha1, long size) {}

    /** The library that holds {@link #agent()}. */
    Optional<Library> agentLibrary() {
        return agent.flatMap(present -> libraries.stream()
                .filter(library -> library.name().equals(present.library()))
                .findFirst());
    }

    /** Opens the bytes of a library jar by its {@link Library#path()}. */
    @FunctionalInterface
    interface LibrarySource {
        InputStream open(String path) throws IOException;
    }

    /** The payload packed into this installer jar. */
    static InstallerPayload bundled() throws IOException {
        ClassLoader loader = InstallerPayload.class.getClassLoader();
        try (InputStream index = loader.getResourceAsStream(INDEX)) {
            if (index == null) {
                throw new IOException(
                        INDEX + " is missing; this installer jar was not built by Plexus Mod Loader's build");
            }
            return parse(new InputStreamReader(index, StandardCharsets.UTF_8), path -> {
                InputStream jar = loader.getResourceAsStream(LIBRARY_ROOT + path);
                if (jar == null) {
                    throw new IOException(LIBRARY_ROOT + path + " is missing from the installer jar");
                }
                return jar;
            });
        }
    }

    static InstallerPayload parse(Reader index, LibrarySource source) {
        JsonObject root = JsonParser.parseReader(index).getAsJsonObject();
        List<String> minecraftVersions = new ArrayList<>();
        for (JsonElement version : root.getAsJsonArray("minecraftVersions")) {
            minecraftVersions.add(version.getAsString());
        }
        List<Library> libraries = new ArrayList<>();
        JsonArray entries = root.getAsJsonArray("libraries");
        for (JsonElement element : entries) {
            JsonObject library = element.getAsJsonObject();
            libraries.add(new Library(
                    library.get("name").getAsString(),
                    library.get("path").getAsString(),
                    library.get("sha1").getAsString(),
                    library.get("size").getAsLong()));
        }
        Optional<Agent> agent = Optional.empty();
        if (root.has("agent")) {
            JsonObject entry = root.getAsJsonObject("agent");
            agent = Optional.of(new Agent(
                    entry.get("library").getAsString(), entry.get("class").getAsString()));
        }
        return new InstallerPayload(
                root.get("plexusVersion").getAsString(),
                minecraftVersions,
                root.get("mainClass").getAsString(),
                libraries,
                agent,
                source);
    }

    /** The newest supported Minecraft version, the installer's default. */
    String newestMinecraftVersion() {
        return minecraftVersions.get(minecraftVersions.size() - 1);
    }
}
