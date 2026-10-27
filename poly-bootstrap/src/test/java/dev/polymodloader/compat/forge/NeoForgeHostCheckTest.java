package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.HostedLaunch;
import dev.polymodloader.runtime.compat.HostPatchCheck;
import dev.polymodloader.runtime.discovery.DiscoveredModFile;
import dev.polymodloader.runtime.discovery.ForeignModResolver;
import dev.polymodloader.runtime.discovery.ModFileScanner;
import dev.polymodloader.runtime.discovery.NestedJarExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Which Fabric and Quilt mods NeoForge's patched client leaves out ({@code POLY-COMPAT-224}) and why, checked as the
 * runtime checks them ({@link HostPatchCheck}) against NeoForge's real files: the NeoForge release Poly Mod Loader pins
 * for the Minecraft version is downloaded and its patches applied to the vanilla client jar ({@link
 * NeoForgeInstallation}), and the injection points of {@link NeoForgeInjectionPoints} are in effect. For the mods named
 * in {@code poly.hostCheckFocus}, the report also has each mixin class and, for each method a mixin targets, that method
 * in the vanilla and in the patched game, so a new injection point replacement can be read off the bytecode.
 *
 * <p>Run with {@code :poly-bootstrap:neoForgeHostCheck -PhostCheckMods=<folder of mod jars>}; the report is written to
 * {@code build/reports/poly/neoforge-host-check.txt} and printed.
 */
@EnabledIfSystemProperty(named = "poly.hostCheckMods", matches = ".+")
class NeoForgeHostCheckTest {

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

    @Test
    void reportsTheModsNeoForgesPatchLeavesOut() throws IOException {
        Path modsDirectory = Path.of(System.getProperty("poly.hostCheckMods"));
        JsonObject spec = JsonParser.parseString(
                        Files.readString(Path.of(System.getProperty("poly.hostCheckSpec")), StandardCharsets.UTF_8))
                .getAsJsonObject();
        String gameVersion = spec.get("gameVersion").getAsString();
        Path vanillaGame = Path.of(spec.get("gameJar").getAsString());
        Path cache = Path.of(System.getProperty("poly.hostCheckCache"));
        Set<String> focus = new LinkedHashSet<>(
                Arrays.stream(System.getProperty("poly.hostCheckFocus", "").split(","))
                        .map(String::strip)
                        .filter(id -> !id.isEmpty())
                        .toList());

        NeoForgeInstallation.Release release = NeoForgeInstallation.release(gameVersion)
                .orElseThrow(() -> new IllegalStateException("no NeoForge release for " + gameVersion));
        NeoForgeInstallation.Prepared files = NeoForgeInstallation.prepare(
                release,
                cache.resolve("neoforge"),
                GameSide.CLIENT,
                vanillaGame,
                ForgeInstallation.httpDownloader(),
                line -> {});

        List<URL> libraries = new ArrayList<>();
        for (JsonElement library : spec.getAsJsonArray("libraries")) {
            libraries.add(Path.of(library.getAsString()).toUri().toURL());
        }
        for (Path library : files.libraries()) {
            libraries.add(library.toUri().toURL());
        }
        List<Path> jars;
        try (Stream<Path> found = Files.walk(modsDirectory)) {
            jars = found.filter(file -> file.toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        }
        assertFalse(jars.isEmpty(), "no mod jars in " + modsDirectory);

        // The details first and the verdicts last, so the verdicts end the output (CI logs keep the end).
        StringBuilder report = new StringBuilder();
        StringBuilder summary = new StringBuilder();
        summary.append("\n================ verdicts ================\nNeoForge ")
                .append(release.neoForgeVersion())
                .append(" on Minecraft ")
                .append(gameVersion)
                .append(" (client), ")
                .append(jars.size())
                .append(" jar(s) from ")
                .append(modsDirectory)
                .append('\n');
        try (URLClassLoader platform =
                new URLClassLoader(libraries.toArray(URL[]::new), getClass().getClassLoader())) {
            ForeignModResolver bridges = new ForeignModResolver(getClass().getClassLoader());
            List<ForeignModResolver.ForeignMod> foreign = new ArrayList<>();
            for (DiscoveredModFile file : new ModFileScanner().scan(modsDirectory.resolve("none"), jars)) {
                bridges.assess(file, GameSide.CLIENT).ifPresent(foreign::add);
            }
            List<ForeignModResolver.ForeignMod> mods = new NestedJarExtractor(
                            bridges, cache.resolve("nested-" + gameVersion), GameSide.CLIENT)
                    .expand(foreign)
                    .mods();
            HostedLaunch host = new HostedLaunch(
                    "NeoForge " + release.neoForgeVersion(),
                    List.of(),
                    NeoForgeInjectionPoints.all(),
                    vanillaGame,
                    true);
            List<ForeignModResolver.ForeignMod> checked = HostPatchCheck.apply(
                    GameSide.CLIENT,
                    host,
                    List.of(files.patchedGame(), files.universal()),
                    platform,
                    bridges.bridges(),
                    mods);

            int leftOut = 0;
            Map<String, Path> focusJars = new LinkedHashMap<>();
            for (ForeignModResolver.ForeignMod mod : checked) {
                String modId = mod.assessment().modId();
                if (focus.contains(modId)) {
                    focusJars.putIfAbsent(modId, mod.jar());
                }
                for (CompatibilityFinding finding : mod.assessment().findings()) {
                    if (finding.code().equals(HostPatchCheck.CODE)) {
                        leftOut++;
                        summary.append("\n[left out] ")
                                .append(modId)
                                .append(' ')
                                .append(mod.assessment().version())
                                .append('\n');
                        for (String reason : finding.detail().split(" \\| ")) {
                            summary.append("    ").append(reason).append('\n');
                        }
                    }
                }
            }
            summary.append("\n")
                    .append(leftOut)
                    .append(" of ")
                    .append(checked.size())
                    .append(" mod(s) left out with ")
                    .append(HostPatchCheck.CODE)
                    .append('\n');

            for (Map.Entry<String, Path> entry : focusJars.entrySet()) {
                describeMixins(entry.getKey(), entry.getValue(), vanillaGame, files.patchedGame(), report);
            }
        }
        // More methods to read, as <internal class name>#<method name>, from the vanilla game, the patched game and
        // NeoForge's universal jar (its hooks).
        String dump = System.getProperty("poly.hostCheckDump", "");
        if (!dump.isBlank()) {
            report.append("\n================ requested methods ================\n");
            try (JarFile vanilla = new JarFile(vanillaGame.toFile());
                    JarFile patched = new JarFile(files.patchedGame().toFile());
                    JarFile universal = new JarFile(files.universal().toFile())) {
                for (String request : dump.split(",")) {
                    String[] parts = request.strip().split("#", 2);
                    if (parts.length != 2) {
                        continue;
                    }
                    describeMethod("vanilla", vanilla, parts[0], parts[1], report);
                    describeMethod("NeoForge", patched, parts[0], parts[1], report);
                    describeMethod("NeoForge universal", universal, parts[0], parts[1], report);
                }
            }
        }
        report.append(summary);
        Path out = Path.of(System.getProperty("poly.hostCheckReport")).resolve("neoforge-host-check.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);
    }

    /** {@code modId}'s mixin classes, then each method they target in the vanilla and the patched game. */
    private static void describeMixins(String modId, Path jar, Path vanillaGame, Path patchedGame, StringBuilder report)
            throws IOException {
        report.append("\n================ ").append(modId).append(" mixins ================\n");
        Map<String, Set<String>> targets = new LinkedHashMap<>();
        try (JarFile mod = new JarFile(jar.toFile())) {
            JsonObject metadata = JsonParser.parseString(read(mod, "fabric.mod.json")
                            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                            .orElse("{}"))
                    .getAsJsonObject();
            List<String> configs = new ArrayList<>();
            if (metadata.has("mixins")) {
                for (JsonElement element : metadata.getAsJsonArray("mixins")) {
                    configs.add(
                            element.isJsonObject()
                                    ? element.getAsJsonObject().get("config").getAsString()
                                    : element.getAsString());
                }
            }
            for (String configName : configs) {
                Optional<byte[]> configBytes = read(mod, configName);
                if (configBytes.isEmpty()) {
                    continue;
                }
                JsonObject config = JsonParser.parseString(new String(configBytes.get(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String packageName =
                        config.has("package") ? config.get("package").getAsString() : "";
                for (String list : List.of("mixins", "client", "server")) {
                    if (!config.has(list)) {
                        continue;
                    }
                    for (JsonElement name : config.getAsJsonArray(list)) {
                        String className = (packageName + "." + name.getAsString()).replace('.', '/');
                        Optional<byte[]> bytes = read(mod, className + ".class");
                        if (bytes.isEmpty()) {
                            continue;
                        }
                        report.append("\n---- ")
                                .append(configName)
                                .append(" (")
                                .append(list)
                                .append(") ")
                                .append(className)
                                .append('\n');
                        // Signatures and annotations: where each injector points is in its annotations.
                        StringWriter text = new StringWriter();
                        new ClassReader(bytes.get())
                                .accept(
                                        new TraceClassVisitor(null, new Textifier(), new PrintWriter(text)),
                                        ClassReader.SKIP_CODE);
                        report.append(text);
                        collectTargets(bytes.get(), targets);
                    }
                }
            }
        }
        report.append("\n================ ").append(modId).append(" targets ================\n");
        try (JarFile vanilla = new JarFile(vanillaGame.toFile());
                JarFile patched = new JarFile(patchedGame.toFile())) {
            for (Map.Entry<String, Set<String>> target : targets.entrySet()) {
                for (String method : target.getValue()) {
                    StringBuilder before = new StringBuilder();
                    StringBuilder after = new StringBuilder();
                    describeMethod("vanilla", vanilla, target.getKey(), method, before);
                    describeMethod("NeoForge", patched, target.getKey(), method, after);
                    // Only what NeoForge changed matters; line numbers move with any patch to the file.
                    if (withoutLineNumbers(before).equals(withoutLineNumbers(after))) {
                        report.append("\n---- ")
                                .append(target.getKey())
                                .append('.')
                                .append(method)
                                .append(": unchanged by NeoForge\n");
                    } else {
                        report.append(before).append(after);
                    }
                }
            }
        }
    }

    /** The classes {@code mixin} targets and, per class, the names of the methods its injectors name. */
    private static void collectTargets(byte[] mixin, Map<String, Set<String>> targets) {
        ClassNode node = new ClassNode();
        new ClassReader(mixin).accept(node, ClassReader.SKIP_CODE);
        List<String> classes = new ArrayList<>();
        for (AnnotationNode annotation : annotations(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (!annotation.desc.equals(MIXIN) || annotation.values == null) {
                continue;
            }
            for (int i = 0; i < annotation.values.size(); i += 2) {
                Object value = annotation.values.get(i + 1);
                if (annotation.values.get(i).equals("value") && value instanceof List<?> types) {
                    types.forEach(type -> classes.add(((Type) type).getInternalName()));
                } else if (annotation.values.get(i).equals("targets") && value instanceof List<?> names) {
                    names.forEach(name -> classes.add(name.toString().replace('.', '/')));
                }
            }
        }
        Set<String> methods = new LinkedHashSet<>();
        for (MethodNode method : node.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (annotation.values == null) {
                    continue;
                }
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (annotation.values.get(i).equals("method")
                            && annotation.values.get(i + 1) instanceof List<?> names) {
                        for (Object name : names) {
                            String selector = name.toString();
                            int owner = selector.indexOf(';');
                            if (selector.startsWith("L") && owner > 0) {
                                selector = selector.substring(owner + 1);
                            }
                            int descriptor = selector.indexOf('(');
                            methods.add(descriptor >= 0 ? selector.substring(0, descriptor) : selector);
                        }
                    }
                }
            }
        }
        for (String target : classes) {
            targets.computeIfAbsent(target, key -> new LinkedHashSet<>()).addAll(methods);
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) {
            all.addAll(visible);
        }
        if (invisible != null) {
            all.addAll(invisible);
        }
        return all;
    }

    /** Every method named {@code method} of {@code className} in {@code jar}, as ASM's text. */
    private static void describeMethod(String label, JarFile jar, String className, String method, StringBuilder report)
            throws IOException {
        Optional<byte[]> bytes = read(jar, className + ".class");
        report.append("\n---- ")
                .append(label)
                .append(' ')
                .append(className)
                .append('.')
                .append(method)
                .append('\n');
        if (bytes.isEmpty()) {
            report.append("    (no such class)\n");
            return;
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes.get()).accept(node, 0);
        boolean any = false;
        for (MethodNode candidate : node.methods) {
            if (!candidate.name.equals(method)) {
                continue;
            }
            any = true;
            Textifier textifier = new Textifier();
            candidate.accept(new TraceMethodVisitor(textifier));
            StringWriter text = new StringWriter();
            textifier.print(new PrintWriter(text));
            report.append("  ").append(candidate.name).append(candidate.desc).append('\n');
            report.append(text);
        }
        if (!any) {
            report.append("    (no such method)\n");
        }
    }

    /** {@link #describeMethod}'s {@code text} without its heading, which names the jar, and its line numbers. */
    private static String withoutLineNumbers(StringBuilder text) {
        return text.toString()
                .lines()
                .skip(2)
                .filter(line -> !line.strip().startsWith("LINENUMBER"))
                .toList()
                .toString();
    }

    private static Optional<byte[]> read(JarFile jar, String entryName) throws IOException {
        ZipEntry entry = jar.getEntry(entryName);
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return Optional.of(input.readAllBytes());
        }
    }
}
