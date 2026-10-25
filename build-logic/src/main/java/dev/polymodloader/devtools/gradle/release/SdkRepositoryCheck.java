package dev.polymodloader.devtools.gradle.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Checks a Maven repository written by {@code publishSdk} (ADR-0008): every expected artifact of the version
 * is there and listed in its {@code maven-metadata.xml}, JavaDoc only where expected, no other Poly Mod Loader
 * artifact of that version, every Poly Mod Loader dependency of a POM inside the repository, and, for every
 * version in the repository, no {@code -sources.jar} and no {@code .java} file inside any archive.
 */
public final class SdkRepositoryCheck {

    /** The group of Poly Mod Loader's own artifacts; dependencies in it must be in the repository. */
    public static final String GROUP = "dev.polymodloader";

    private SdkRepositoryCheck() {}

    /**
     * An artifact {@code publishSdk} publishes.
     *
     * @param packaging {@code jar}, {@code zip}, or {@code pom} for a POM without a file (plugin marker)
     * @param javadoc whether the version has a {@code -javadoc.jar}
     */
    public record Artifact(String group, String name, String packaging, boolean javadoc) {

        Path directory(Path repository) {
            return repository.resolve(group.replace('.', '/')).resolve(name);
        }
    }

    /** Returns the problems found; an empty list means the repository is a correct SDK repository. */
    public static List<String> problems(Path repository, String version, Collection<Artifact> expected) {
        List<String> problems = new ArrayList<>();
        if (!Files.isDirectory(repository)) {
            return List.of(repository + " does not exist");
        }
        Map<String, Artifact> byName = new TreeMap<>();
        for (Artifact artifact : expected) {
            byName.put(artifact.group() + ":" + artifact.name(), artifact);
        }
        for (Artifact artifact : expected) {
            checkArtifact(repository, version, artifact, byName, problems);
        }
        checkNoOtherArtifacts(repository, version, byName, problems);
        checkNoSources(repository, problems);
        return problems;
    }

    private static void checkArtifact(
            Path repository, String version, Artifact artifact, Map<String, Artifact> expected, List<String> problems) {
        Path directory = artifact.directory(repository);
        Path versionDirectory = directory.resolve(version);
        String base = artifact.name() + "-" + version;
        Path pom = versionDirectory.resolve(base + ".pom");
        String coordinates = artifact.group() + ":" + artifact.name() + ":" + version;
        if (!Files.isRegularFile(pom)) {
            problems.add(coordinates + ": no " + repository.relativize(pom));
            return;
        }
        if (!artifact.packaging().equals("pom")
                && !Files.isRegularFile(versionDirectory.resolve(base + "." + artifact.packaging()))) {
            problems.add(coordinates + ": no " + base + "." + artifact.packaging());
        }
        boolean javadoc = Files.isRegularFile(versionDirectory.resolve(base + "-javadoc.jar"));
        if (javadoc != artifact.javadoc()) {
            problems.add(coordinates + (artifact.javadoc() ? ": the JavaDoc jar is missing" : ": has a JavaDoc jar,"
                    + " but only the public API modules mods compile against carry one"));
        }
        Path metadata = directory.resolve("maven-metadata.xml");
        if (!Files.isRegularFile(metadata)) {
            problems.add(coordinates + ": no maven-metadata.xml");
        } else if (!readString(metadata).contains("<version>" + version + "</version>")) {
            problems.add(coordinates + ": maven-metadata.xml does not list " + version);
        }
        Path module = versionDirectory.resolve(base + ".module");
        if (Files.isRegularFile(module)) {
            checkModuleVariants(coordinates, module, problems);
        }
        for (String[] dependency : dependencies(pom)) {
            if (!dependency[0].equals(GROUP)) {
                continue;
            }
            String key = dependency[0] + ":" + dependency[1];
            if (!expected.containsKey(key)) {
                problems.add(coordinates + " depends on " + key + ", which is not part of the SDK");
            } else if (!version.equals(dependency[2])) {
                problems.add(coordinates + " depends on " + key + ":" + dependency[2] + " instead of " + version);
            }
        }
    }

    /**
     * Every variant of the Gradle module metadata has a file. Gradle prefers the metadata over the POM, and a
     * variant without files (a class folder variant published by mistake) gives the consumer an empty classpath.
     */
    private static void checkModuleVariants(String coordinates, Path module, List<String> problems) {
        JsonObject metadata;
        try {
            metadata = JsonParser.parseString(readString(module)).getAsJsonObject();
        } catch (RuntimeException e) {
            problems.add(coordinates + ": unreadable " + module.getFileName() + ": " + e.getMessage());
            return;
        }
        JsonArray variants = metadata.has("variants") ? metadata.getAsJsonArray("variants") : new JsonArray();
        for (JsonElement element : variants) {
            JsonObject variant = element.getAsJsonObject();
            boolean hasFiles = variant.has("files") && !variant.getAsJsonArray("files").isEmpty();
            if (!hasFiles && !variant.has("available-at")) {
                problems.add(coordinates + ": the variant " + variant.get("name").getAsString()
                        + " in " + module.getFileName() + " has no files");
            }
        }
    }

    /** Other Poly Mod Loader artifacts of this version: test mods, examples or the installer published by mistake. */
    private static void checkNoOtherArtifacts(
            Path repository, String version, Map<String, Artifact> expected, List<String> problems) {
        try (Stream<Path> walk = Files.walk(repository)) {
            for (Path pom : walk.filter(file -> file.getFileName().toString().endsWith("-" + version + ".pom"))
                    .toList()) {
                Path artifactDirectory = pom.getParent().getParent();
                String name = artifactDirectory.getFileName().toString();
                String group = repository
                        .relativize(artifactDirectory.getParent())
                        .toString()
                        .replace('\\', '/')
                        .replace('/', '.');
                if (!expected.containsKey(group + ":" + name)) {
                    problems.add(group + ":" + name + ":" + version + " is in the repository but not part of the SDK");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void checkNoSources(Path repository, List<String> problems) {
        try (Stream<Path> walk = Files.walk(repository)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                String relative = repository.relativize(file).toString().replace('\\', '/');
                if (name.endsWith("-sources.jar")) {
                    problems.add(relative + ": source jars are not published");
                } else if (name.endsWith(".jar") || name.endsWith(".zip")) {
                    String source = firstJavaSource(file);
                    if (source != null) {
                        problems.add(relative + " contains the Java source " + source);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The first {@code .java} entry of an archive. The mod template zip is allowed its own example sources
     * below {@code src/}; every other archive must have none.
     */
    private static String firstJavaSource(Path archive) throws IOException {
        boolean template = archive.getFileName().toString().endsWith(".zip");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (entry.endsWith(".java") && !(template && entry.contains("/src/"))) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** {@code [groupId, artifactId, version]} of each dependency the POM declares. */
    private static List<String[]> dependencies(Path pom) {
        try (InputStream input = Files.newInputStream(pom)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(input);
            NodeList nodes = document.getElementsByTagName("dependency");
            List<String[]> result = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element dependency = (Element) nodes.item(i);
                result.add(new String[] {
                    text(dependency, "groupId"), text(dependency, "artifactId"), text(dependency, "version")
                });
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(pom + " is not a readable POM: " + e.getMessage(), e);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
