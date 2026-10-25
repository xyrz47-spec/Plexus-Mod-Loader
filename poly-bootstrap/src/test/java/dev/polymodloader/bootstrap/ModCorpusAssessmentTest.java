package dev.polymodloader.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.BridgedEntrypoint;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.runtime.compat.CrossLoaderModResolver;
import dev.polymodloader.runtime.compat.ModLinkVerifier;
import dev.polymodloader.runtime.discovery.DiscoveredModFile;
import dev.polymodloader.runtime.discovery.ForeignModResolver;
import dev.polymodloader.runtime.discovery.ModFileKind;
import dev.polymodloader.runtime.discovery.ModFileScanner;
import dev.polymodloader.runtime.discovery.NestedJarExtractor;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Assesses a corpus of real mods from Modrinth ({@code :poly-bootstrap:modCorpus}) for both sides without
 * starting the game: every jar alone and all of them together go through discovery, the loader bridges, the
 * cross-loader dependency resolution and the link check against the real game jar and libraries. Nothing may
 * throw, every jar must end up loaded or blocked, and every blocked one must say why with a code. Run with
 * {@code :poly-bootstrap:corpusAssessment}; the verdicts are written to {@code build/reports/poly/}.
 */
@EnabledIfSystemProperty(named = "poly.corpus", matches = ".+")
class ModCorpusAssessmentTest {

    private static final String JAVA = String.valueOf(Runtime.version().feature());

    @ParameterizedTest
    @EnumSource(GameSide.class)
    void everyRealModIsLoadedOrBlockedWithAReason(GameSide side) throws IOException {
        Path corpus = Path.of(System.getProperty("poly.corpus"));
        JsonObject spec = JsonParser.parseString(Files.readString(
                        Path.of(System.getProperty(side == GameSide.CLIENT ? "poly.clientSpec" : "poly.serverSpec")),
                        StandardCharsets.UTF_8))
                .getAsJsonObject();
        String gameVersion = spec.get("gameVersion").getAsString();
        Path gameJar = Path.of(spec.get("gameJar").getAsString());
        List<URL> libraries = new ArrayList<>();
        for (var library : spec.getAsJsonArray("libraries")) {
            libraries.add(Path.of(library.getAsString()).toUri().toURL());
        }
        List<Path> jars;
        try (Stream<Path> files = Files.walk(corpus)) {
            jars = files.filter(file -> file.toString().endsWith(".jar"))
                    .sorted()
                    .toList();
        }
        assertFalse(jars.isEmpty(), "no jars in " + corpus + "; run :poly-bootstrap:modCorpus first");

        try (URLClassLoader platform = new URLClassLoader(
                        libraries.toArray(URL[]::new), getClass().getClassLoader());
                ModLinkVerifier verifier = new ModLinkVerifier(List.of(gameJar), platform, side)) {
            ForeignModResolver bridges = new ForeignModResolver(getClass().getClassLoader());
            CrossLoaderModResolver.LinkCheck linkCheck = (mod, others) -> verifier.verify(
                    mod.jar(),
                    others,
                    mod.bridge(),
                    mod.assessment().entrypoints().stream()
                            .map(BridgedEntrypoint::className)
                            .toList(),
                    mod.assessment().accessWidenings(),
                    mod.assessment().injectedInterfaces());
            Map<Path, String> otherKinds = new LinkedHashMap<>();
            List<ForeignModResolver.ForeignMod> foreign = new ArrayList<>();
            for (DiscoveredModFile file : new ModFileScanner().scan(corpus.resolve("none"), jars)) {
                if (file.kind() == ModFileKind.POLY_NATIVE
                        || file.kind() == ModFileKind.FML_LIBRARY
                        || file.kind() == ModFileKind.UNREADABLE
                        || file.kind() == ModFileKind.UNKNOWN) {
                    otherKinds.put(file.path(), file.kind().name());
                    continue;
                }
                foreign.add(bridges.assess(file, side).orElseThrow());
            }

            // Nested jars are extracted next to the corpus and assessed as mods of their own.
            NestedJarExtractor nested =
                    new NestedJarExtractor(bridges, corpus.resolveSibling(corpus.getFileName() + "-nested"), side);
            // Interfaces any corpus mod injects are known before any mod is checked, as in the runtime.
            List<ForeignModResolver.ForeignMod> everyMod =
                    nested.expand(foreign).mods();
            verifier.absentOnThisSide(everyMod.stream()
                    .filter(mod -> mod.assessment().findings().stream()
                            .anyMatch(finding -> finding.code().equals("POLY-COMPAT-108")))
                    .map(ForeignModResolver.ForeignMod::jar)
                    .toList());
            verifier.injectInterfaces(everyMod.stream()
                    .flatMap(mod -> mod.assessment().injectedInterfaces().stream())
                    .toList());
            Map<String, BridgeAssessment> alone = new LinkedHashMap<>();
            for (ForeignModResolver.ForeignMod mod : foreign) {
                CrossLoaderModResolver.Result result = new CrossLoaderModResolver()
                        .resolve(nested.expand(List.of(mod)).mods(), Map.of(), List.of(), gameVersion, JAVA, linkCheck);
                for (ForeignModResolver.ForeignMod part : result.mods()) {
                    alone.putIfAbsent(key(part), part.assessment());
                }
            }
            List<ForeignModResolver.ForeignMod> expanded =
                    nested.expand(foreign).mods();
            CrossLoaderModResolver.Result together =
                    new CrossLoaderModResolver().resolve(expanded, Map.of(), List.of(), gameVersion, JAVA, linkCheck);
            assertEquals(expanded.size(), together.mods().size());

            JsonArray report = new JsonArray();
            for (ForeignModResolver.ForeignMod mod : together.mods()) {
                BridgeAssessment assessment = mod.assessment();
                if (!assessment.status().loadable()) {
                    assertTrue(
                            assessment.findings().stream().anyMatch(CompatibilityFinding::blocking),
                            mod.jar() + " is blocked without a blocking finding");
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("file", display(corpus, mod));
                entry.addProperty("nested", mod.nested());
                entry.addProperty("loader", assessment.loader());
                entry.addProperty("modId", assessment.modId());
                entry.addProperty("version", assessment.version());
                entry.addProperty("alone", alone.get(key(mod)).status().name());
                entry.addProperty("together", assessment.status().name());
                JsonArray codes = new JsonArray();
                assessment.blockingCodes().forEach(codes::add);
                entry.add("blockingCodes", codes);
                JsonArray findings = new JsonArray();
                assessment.findings().forEach(finding -> findings.add(finding.toString()));
                entry.add("findings", findings);
                report.add(entry);
            }
            otherKinds.forEach((path, kind) -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("file", corpus.relativize(path).toString().replace('\\', '/'));
                entry.addProperty("kind", kind);
                report.add(entry);
            });
            JsonObject summary = new JsonObject();
            summary.addProperty("side", side.name());
            summary.addProperty("gameVersion", gameVersion);
            summary.addProperty("jars", jars.size());
            summary.addProperty("foreignMods", foreign.size());
            summary.addProperty("nestedMods", expanded.size() - foreign.size());
            summary.addProperty("loadedTogether", together.loadable().size());
            summary.addProperty(
                    "loadedTogetherDirect",
                    together.loadable().stream().filter(mod -> !mod.nested()).count());
            summary.addProperty(
                    "loadedAloneDirect",
                    foreign.stream()
                            .filter(mod -> alone.get(key(mod)).status().loadable())
                            .count());
            summary.add("mods", report);
            Path out = Path.of(System.getProperty("poly.corpusReport"))
                    .resolve("corpus-assessment-" + side.name().toLowerCase(java.util.Locale.ROOT) + ".json");
            Files.createDirectories(out.getParent());
            Files.writeString(
                    out,
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .disableHtmlEscaping()
                            .create()
                            .toJson(summary),
                    StandardCharsets.UTF_8);
            System.out.println("[corpus] " + side + ": " + jars.size() + " jars, " + foreign.size() + " foreign mods"
                    + " and " + (expanded.size() - foreign.size()) + " nested, "
                    + together.loadable().size()
                    + " loadable together, report " + out);
        }
    }

    /** Identifies a mod across runs: a nested jar file can sit in several containers. */
    private static String key(ForeignModResolver.ForeignMod mod) {
        return mod.containerJar() + "|" + mod.jar() + "|" + mod.assessment().modId();
    }

    private static String display(Path corpus, ForeignModResolver.ForeignMod mod) {
        if (mod.nested()) {
            return mod.containerJar().getFileName() + "!/" + mod.jar().getFileName();
        }
        return corpus.relativize(mod.jar()).toString().replace('\\', '/');
    }
}
