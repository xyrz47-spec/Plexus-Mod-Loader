package dev.plexusmodloader.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.compat.bridge.SystemClassPath;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mechanism behind Forge mods in an installed Plexus Mod Loader (ADR-0024), in child JVMs started the two ways an
 * installation starts Plexus Mod Loader: the launcher profile's {@code -javaagent:<plexus-bootstrap jar>}, and the server's
 * {@code java -jar plexusmodloader-server.jar}, a manifest-only jar with {@code Launcher-Agent-Class} whose agent class
 * comes from its {@code Class-Path}. A probe appends Forge's four file system jars through {@link SystemClassPathAgent}
 * and lists the file systems Java knows.
 *
 * <p>Needs {@code plexus.agentJar} (the built plexus-bootstrap jar) and {@code plexus.forgeFileSystemJars} (securemodules,
 * unsafe, JarJarFileSystems and roimfs, separated by the path separator); {@code :plexus-bootstrap:test} passes both.
 */
@EnabledIfSystemProperty(named = "plexus.agentJar", matches = ".+")
class SystemClassPathAgentTest {

    private static final List<String> FORGE_SCHEMES = List.of("jij", "path", "roimfs", "union");

    @TempDir
    Path directory;

    @Test
    void theLauncherProfilesJavaAgentAppendsForgesFileSystems() throws Exception {
        List<String> output = run(List.of("-javaagent:" + agentJar(), "@" + classPathArguments()), "append");

        assertTrue(output.contains("agent: present"), output.toString());
        assertEquals(FORGE_SCHEMES, forgeSchemes(output), output.toString());
    }

    @Test
    void theServersLaunchJarLoadsTheAgentFromItsClassPath() throws Exception {
        Path launchJar = launchJar();

        List<String> output = run(List.of("-jar", launchJar.toString()), null);

        assertTrue(output.contains("agent: present"), output.toString());
        assertEquals(FORGE_SCHEMES, forgeSchemes(output), output.toString());
    }

    /** Without the agent, nothing can be appended (a development run has the jars on its class path instead). */
    @Test
    void withoutTheAgentThereIsNothingToAppendTo() throws Exception {
        List<String> output = run(List.of("@" + classPathArguments()), "append");

        assertTrue(output.contains("agent: absent"), output.toString());
        assertEquals(List.of(), forgeSchemes(output), output.toString());
    }

    /** The trap ForgeFileSystems reports: Java lists its file systems once, and later jars add none. */
    @Test
    void javaListsItsFileSystemsOnlyOnce() throws Exception {
        List<String> output = run(List.of("-javaagent:" + agentJar(), "@" + classPathArguments()), "list-first");

        assertTrue(output.contains("agent: present"), output.toString());
        assertTrue(output.contains("union provider class: visible"), output.toString());
        assertEquals(List.of(), forgeSchemes(output), output.toString());
    }

    /** The child JVM's main class. Arguments: {@code list-first} or {@code append}, then the jars to append. */
    public static final class Probe {
        private Probe() {}

        public static void main(String[] args) throws IOException {
            if (args[0].equals("list-first")) {
                System.out.println("before: " + schemes());
            }
            Optional<SystemClassPath> classPath = SystemClassPathAgent.systemClassPath();
            System.out.println("agent: " + (classPath.isPresent() ? "present" : "absent"));
            if (classPath.isPresent()) {
                for (int i = 1; i < args.length; i++) {
                    classPath.get().append(Path.of(args[i]));
                }
            }
            boolean visible =
                    ClassLoader.getSystemClassLoader().getResource("cpw/mods/niofs/union/UnionFileSystemProvider.class")
                            != null;
            System.out.println("union provider class: " + (visible ? "visible" : "not visible"));
            System.out.println("schemes: " + schemes());
        }

        private static String schemes() {
            return FileSystemProvider.installedProviders().stream()
                    .map(FileSystemProvider::getScheme)
                    .collect(Collectors.toCollection(TreeSet::new))
                    .stream()
                    .collect(Collectors.joining(","));
        }
    }

    private static Path agentJar() {
        return Path.of(System.getProperty("plexus.agentJar"));
    }

    private static List<String> forgeJars() {
        return Arrays.stream(System.getProperty("plexus.forgeFileSystemJars").split(File.pathSeparator))
                .filter(path -> !path.isBlank())
                .toList();
    }

    /**
     * An argument file with this test's class path (too long for a Windows command line otherwise); forward slashes,
     * because a backslash escapes inside quotes there.
     */
    private Path classPathArguments() throws IOException {
        Path file = directory.resolve("classpath.args");
        String classPath = System.getProperty("java.class.path").replace('\\', '/');
        Files.writeString(file, "-cp \"" + classPath + "\"\n", StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Like the installer's {@code plexusmodloader-server.jar}: only a manifest, with the probe as main class, the agent as
     * {@code Launcher-Agent-Class}, and the class path (agent jar first) in {@code Class-Path}. The jars to append are
     * listed in {@code forge-jars.txt} next to it ({@link LaunchJarProbe}).
     */
    private Path launchJar() throws IOException {
        List<String> classPath = new ArrayList<>();
        classPath.add(agentJar().toUri().toString());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                classPath.add(Path.of(entry).toUri().toString());
            }
        }
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, LaunchJarProbe.class.getName());
        attributes.put(new Attributes.Name("Launcher-Agent-Class"), SystemClassPathAgent.class.getName());
        attributes.put(Attributes.Name.CLASS_PATH, String.join(" ", classPath));
        Path jar = directory.resolve("launch.jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file, manifest)) {
            out.flush();
        }
        Files.writeString(directory.resolve("forge-jars.txt"), String.join("\n", forgeJars()), StandardCharsets.UTF_8);
        return jar;
    }

    /** The launch jar's main class: {@link Probe} with the Forge jars listed in {@code forge-jars.txt}. */
    public static final class LaunchJarProbe {
        private LaunchJarProbe() {}

        public static void main(String[] args) throws IOException {
            List<String> probeArgs = new ArrayList<>();
            probeArgs.add("append");
            probeArgs.addAll(Files.readAllLines(Path.of("forge-jars.txt"), StandardCharsets.UTF_8));
            Probe.main(probeArgs.toArray(String[]::new));
        }
    }

    /** Runs a child JVM in {@link #directory} and returns its output lines. */
    private List<String> run(List<String> jvmArguments, String mode) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmArguments);
        if (mode != null) {
            command.add(Probe.class.getName());
            command.add(mode);
            command.addAll(forgeJars());
        }
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the probe JVM did not end:\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertFalse(output.contains("WARNING"), "the JVM warned:\n" + output);
        return output.lines().map(String::strip).toList();
    }

    /** Forge's schemes among the file systems the probe listed last. */
    private static List<String> forgeSchemes(List<String> output) {
        String schemes = output.stream()
                .filter(line -> line.startsWith("schemes: "))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no schemes line in " + output))
                .substring("schemes: ".length());
        return Arrays.stream(schemes.split(","))
                .filter(FORGE_SCHEMES::contains)
                .sorted()
                .toList();
    }
}
