package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What FML 11 would refuse in neoforge.mods.toml is left out before it starts, with the mods that need it. */
class NeoForgeModRequirementsTest {

    private static final String MINECRAFT = "26.2";
    private static final String NEOFORGE = "26.2.0.93";
    private static final String JAVAFML = "11.0";

    @TempDir
    Path directory;

    private static ForgeModRequirements.VersionRanges ranges() throws ReflectiveOperationException {
        return ForgeModRequirements.maven(NeoForgeModRequirementsTest.class.getClassLoader());
    }

    private static ForgeModRequirements.Result check(List<Path> jars, GameSide side)
            throws ReflectiveOperationException {
        return NeoForgeModRequirements.check(jars, MINECRAFT, NEOFORGE, JAVAFML, side, ranges());
    }

    @Test
    void aModForAnotherNeoForgeOrMinecraftIsLeftOutWithTheModsThatNeedIt() throws Exception {
        Path library = jar("lib.jar", modsToml("javafml", "[1,)", "somelib", "3.0", """
                [[dependencies.somelib]]
                modId="neoforge"
                type="required"
                versionRange="[26.3,)"
                [[dependencies.somelib]]
                modId="minecraft"
                type="required"
                versionRange="[26.3, 26.4)"
                """));
        Path user = jar("user.jar", modsToml("javafml", "[1,)", "user", "1.0", """
                [[dependencies.user]]
                modId="somelib"
                type="required"
                versionRange="[3,)"
                """));
        Path fine = jar("fine.jar", modsToml("javafml", "[4,)", "fine", "1.0", """
                [[dependencies.fine]]
                modId="neoforge"
                type="required"
                versionRange="[26.2,)"
                [[dependencies.fine]]
                modId="jei"
                type="optional"
                versionRange="[99,)"
                """));

        ForgeModRequirements.Result result = check(List.of(library, user, fine), GameSide.DEDICATED_SERVER);

        assertEquals(List.of(fine), result.kept());
        assertEquals(2, result.excluded().size());
        assertEquals("somelib", result.excluded().get(0).modId());
        String libraryReasons = String.join("; ", result.excluded().get(0).reasons());
        assertTrue(libraryReasons.contains("requires neoforge [26.3,), but 26.2.0.93 is installed"), libraryReasons);
        assertTrue(
                libraryReasons.contains("requires minecraft [26.3, 26.4), but Minecraft 26.2 is running"),
                libraryReasons);
        String userReasons = String.join("; ", result.excluded().get(1).reasons());
        assertTrue(
                userReasons.contains("requires somelib [3,), which is installed but NOT loaded (lib.jar)"),
                userReasons);
    }

    /** FML 11 reads {@code type}; without it a dependency is required, whatever a Forge-style {@code mandatory} says. */
    @Test
    void aDependencyWithoutTypeIsRequiredAndOptionalOnesOnlyCheckTheirVersion() throws Exception {
        Path oldStyle = jar("old.jar", modsToml("javafml", "[1,)", "old", "1.0", """
                [[dependencies.old]]
                modId="missing"
                mandatory=false
                versionRange="[1,)"
                """));
        Path optional = jar("optional.jar", modsToml("javafml", "[1,)", "opt", "1.0", """
                [[dependencies.opt]]
                modId="absent"
                type="optional"
                versionRange="[1,)"
                [[dependencies.opt]]
                modId="neoforge"
                type="OPTIONAL"
                versionRange="[27,)"
                """));
        Path discouraged = jar("discouraged.jar", modsToml("javafml", "[1,)", "disc", "1.0", """
                [[dependencies.disc]]
                modId="minecraft"
                type="discouraged"
                versionRange="[26.2]"
                """));

        ForgeModRequirements.Result result = check(List.of(oldStyle, optional, discouraged), GameSide.DEDICATED_SERVER);

        assertEquals(List.of(discouraged), result.kept());
        assertTrue(
                result.excluded().get(0).reasons().getFirst().contains("requires missing [1,), which is not installed"),
                result.excluded().toString());
        assertTrue(
                result.excluded().get(1).reasons().getFirst().contains("requires neoforge [27,), but 26.2.0.93"),
                result.excluded().toString());
    }

    @Test
    void theJavaLanguageLoaderVersionIsChecked() throws Exception {
        Path tooNew = jar("new.jar", modsToml("javafml", "[12,)", "toonew", "1.0", ""));
        Path lowCode = jar("lowcode.jar", modsToml("lowcodefml", "[99,)", "lowcode", "1.0", ""));
        Path other = jar("kotlin.jar", modsToml("kotlinforforge", "[99,)", "kotlinmod", "1.0", ""));
        Path none = jar("none.jar", "license=\"MIT\"\n[[mods]]\nmodId=\"none\"\n");

        ForgeModRequirements.Result result = check(List.of(tooNew, lowCode, other, none), GameSide.CLIENT);

        assertEquals(List.of(lowCode, other, none), result.kept());
        assertTrue(
                result.excluded().getFirst().reasons().getFirst().contains("javafml must be [12,), FML has 11.0"),
                result.excluded().toString());
    }

    @Test
    void incompatibleModsAndSidesAreRespected() throws Exception {
        Path bundle = jar(
                "bundle.jar",
                Map.of(
                        "META-INF/neoforge.mods.toml",
                        modsToml("javafml", "[1,)", "bundle", "1.0", ""),
                        "META-INF/jarjar/lib.jar",
                        nestedJar(modsToml("javafml", "[1,)", "nestedlib", "2.0", ""), null),
                        "META-INF/jarjar/metadata.json",
                        jarJarMetadata("META-INF/jarjar/lib.jar")));
        Path user = jar("user.jar", modsToml("javafml", "[1,)", "user", "1.0", """
                [[dependencies.user]]
                modId="nestedlib"
                type="required"
                versionRange="[2,)"
                [[dependencies.user]]
                modId="clientlib"
                type="required"
                side="CLIENT"
                """));
        Path enemy = jar("enemy.jar", modsToml("javafml", "[1,)", "enemy", "1.0", """
                [[dependencies.enemy]]
                modId="nestedlib"
                type="incompatible"
                versionRange="[2,3)"
                """));
        Path library = jar("plain-library.jar", Map.of("com/example/Lib.class", new byte[] {1}));

        ForgeModRequirements.Result server = check(List.of(bundle, user, enemy, library), GameSide.DEDICATED_SERVER);
        ForgeModRequirements.Result client = check(List.of(bundle, user, library), GameSide.CLIENT);

        assertEquals(
                List.of(bundle, user, library), server.kept(), server.excluded().toString());
        assertTrue(server.excluded().getFirst().reasons().getFirst().contains("incompatible with nestedlib 2.0"));
        assertEquals(List.of(bundle, library), client.kept());
        assertTrue(client.excluded().getFirst().reasons().getFirst().contains("clientlib"));
    }

    @Test
    void anUnreadableFileAndAnUnknownDependencyTypeAreLeftOut() throws Exception {
        Path broken = jar("broken.jar", "this is = = not toml");
        Path badType = jar("badtype.jar", modsToml("javafml", "[1,)", "badtype", "1.0", """
                [[dependencies.badtype]]
                modId="minecraft"
                type="needed"
                """));

        ForgeModRequirements.Result result = check(List.of(broken, badType), GameSide.CLIENT);

        assertEquals(List.of(), result.kept());
        assertTrue(result.excluded().get(0).reasons().getFirst().contains("cannot be read"));
        assertTrue(
                result.excluded().get(1).reasons().getFirst().contains("needed"),
                result.excluded().toString());
    }

    /** {@code ${file.jarVersion}} is the manifest's version; a mod without one is {@code 0.0NONE}, which FML accepts. */
    @Test
    void theJarVersionComesFromTheManifest() throws Exception {
        Path versioned = jar(
                "versioned.jar",
                Map.of("META-INF/neoforge.mods.toml", modsToml("javafml", "[1,)", "lib", "${file.jarVersion}", "")),
                "4.5.6");
        Path unversioned = jar("unversioned.jar", modsToml("javafml", "[1,)", "lib2", "${file.jarVersion}", ""));
        Path user = jar("user.jar", modsToml("javafml", "[1,)", "user", "1.0", """
                [[dependencies.user]]
                modId="lib"
                versionRange="[4.5,)"
                [[dependencies.user]]
                modId="lib2"
                versionRange="[9,)"
                """));

        ForgeModRequirements.Result result = check(List.of(versioned, unversioned, user), GameSide.CLIENT);

        assertEquals(
                List.of(versioned, unversioned, user),
                result.kept(),
                result.excluded().toString());
        assertEquals(
                "4.5.6",
                NeoForgeModRequirements.hostMods(List.of(versioned)).getFirst().version());
    }

    /** The mods FML runs, as other loaders and Poly Mods see them: own mods first, then nested ones. */
    @Test
    void hostModsListEveryModNestedOnesWithTheirContainer() throws Exception {
        Path bundle = jar(
                "bundle.jar",
                Map.of(
                        "META-INF/neoforge.mods.toml",
                        modsToml("javafml", "[1,)", "bundle", "1.0", "")
                                + "displayName=\"The Bundle\"\n[[mods]]\nmodId=\"bundle_extra\"\nversion=\"1.1\"\n",
                        "META-INF/jarjar/lib.jar",
                        nestedJar(modsToml("javafml", "[1,)", "nestedlib", "${file.jarVersion}", ""), "2.0.1"),
                        "META-INF/jarjar/metadata.json",
                        jarJarMetadata("META-INF/jarjar/lib.jar")));
        Path library = jar("plain-library.jar", Map.of("com/example/Lib.class", new byte[] {1}));

        List<BridgeLaunchContext.LoadedMod> mods = NeoForgeModRequirements.hostMods(List.of(bundle, library));

        assertEquals(
                List.of("bundle", "bundle_extra", "nestedlib"),
                mods.stream().map(BridgeLaunchContext.LoadedMod::modId).toList());
        BridgeLaunchContext.LoadedMod first = mods.getFirst();
        assertEquals("NeoForge", first.loader());
        assertEquals("The Bundle", first.name());
        assertTrue(first.runByHost());
        BridgeLaunchContext.LoadedMod nested = mods.get(2);
        assertEquals("2.0.1", nested.version());
        assertEquals(bundle, nested.containerJar());
        assertEquals("META-INF/jarjar/lib.jar", nested.nestedEntry());
    }

    /**
     * FML installs the jars JarJar's {@code META-INF/jarjar/metadata.json} names, wherever they are in the jar: Lambda
     * Dynamic Lights 4.11.2 (26.1.2) names jars in {@code META-INF/jars/}, where its Fabric build has them too. A jar in
     * {@code META-INF/jarjar/} that the metadata does not name is not installed.
     */
    @Test
    void nestedJarsAreTheOnesJarJarMetadataNames() throws Exception {
        Path bundle = jar(
                "lambdynamiclights.jar",
                Map.of(
                        "META-INF/neoforge.mods.toml",
                        modsToml("javafml", "[1,)", "lambdynlights", "4.11.2", """
                                [[dependencies.lambdynlights]]
                                modId="lambdynlights_api"
                                type="required"
                                versionRange="[4.11.2,)"
                                """),
                        "META-INF/jars/lambdynamiclights-api.jar",
                        nestedJar(modsToml("javafml", "[1,)", "lambdynlights_api", "4.11.2", ""), null),
                        "META-INF/jarjar/unlisted.jar",
                        nestedJar(modsToml("javafml", "[1,)", "unlisted_lib", "1.0", ""), null),
                        "META-INF/jarjar/metadata.json",
                        jarJarMetadata("META-INF/jars/lambdynamiclights-api.jar")));
        Path user = jar("user.jar", modsToml("javafml", "[1,)", "user", "1.0", """
                [[dependencies.user]]
                modId="unlisted_lib"
                type="required"
                """));

        ForgeModRequirements.Result result = check(List.of(bundle, user), GameSide.CLIENT);

        assertEquals(List.of(bundle), result.kept(), result.excluded().toString());
        assertTrue(result.excluded().getFirst().reasons().getFirst().contains("unlisted_lib"));
        assertEquals(
                List.of("lambdynlights", "lambdynlights_api"),
                NeoForgeModRequirements.hostMods(List.of(bundle)).stream()
                        .map(BridgeLaunchContext.LoadedMod::modId)
                        .toList());
    }

    /**
     * A game library without {@code neoforge.mods.toml} whose only content is JarJar: MezzConfig 0.6.8 for NeoForge
     * ships as {@code mezz_config-<mc>-neoforge-<version>-standalone.jar} ({@code FMLModType: GAMELIBRARY}) with the mod
     * in {@code META-INF/jarjar/}. FML installs that mod, so it is a host mod; when it was missing from the host mods,
     * MezzConfig's Fabric build ran next to it under the same ID and the server stopped ("More than one MezzConfig
     * physical-side provider is present", corpusFabricNeoForge 2026-10-27).
     */
    @Test
    void aGameLibraryWithOnlyJarJarInstallsItsNestedMods() throws Exception {
        Path standalone = jar(
                "mezz_config-26.2-neoforge-0.6.8-standalone.jar",
                Map.of(
                        "META-INF/jarjar/mezz_config-26.2-neoforge-0.6.8.jar",
                        nestedJar(modsToml("javafml", "[1,)", "mezz_config", "${file.jarVersion}", ""), "0.6.8"),
                        "META-INF/jarjar/metadata.json",
                        jarJarMetadata("META-INF/jarjar/mezz_config-26.2-neoforge-0.6.8.jar")));
        Path user = jar("jei.jar", modsToml("javafml", "[1,)", "jei", "30.47.0", """
                [[dependencies.jei]]
                modId="mezz_config"
                type="required"
                versionRange="[0.6.8,)"
                """));

        List<BridgeLaunchContext.LoadedMod> mods = NeoForgeModRequirements.hostMods(List.of(standalone, user));
        ForgeModRequirements.Result result = check(List.of(standalone, user), GameSide.DEDICATED_SERVER);

        assertEquals(
                List.of("jei", "mezz_config"),
                mods.stream().map(BridgeLaunchContext.LoadedMod::modId).toList());
        BridgeLaunchContext.LoadedMod nested = mods.get(1);
        assertEquals("0.6.8", nested.version());
        assertEquals(standalone, nested.containerJar());
        assertEquals("META-INF/jarjar/mezz_config-26.2-neoforge-0.6.8.jar", nested.nestedEntry());
        assertEquals(List.of(standalone, user), result.kept(), result.excluded().toString());
    }

    /**
     * {@code [modproperties.<id>]} is free-form: keys such as {@code "sodium:config_api_user"} or {@code
     * "yumi:entrypoints"} (Lambda Dynamic Lights 4.12.4) are valid TOML, and FML reads such a file. From 1.2.2 to
     * 1.2.3 Poly Mod Loader looked up {@code fabric:provides} as a dotted key, which fails on {@code :}, so every NeoForge
     * mod with a {@code modproperties} table was left out with POLY-COMPAT-217 (JEI, Kotlin for Forge, Lithium,
     * MoreCulling, Lambda Dynamic Lights; corpusFabricNeoForge 2026-10-27).
     */
    @Test
    void modPropertiesWithColonKeysAreReadAndFabricProvidesIsKept() throws Exception {
        Path lights = jar("lambdynamiclights.jar", modsToml("javafml", "[2,)", "lambdynlights", "4.12.4", "") + """
                        [modproperties.lambdynlights]
                        catalogueImageIcon = "assets/lambdynlights/icon.png"
                        "sodium:config_api_user" = "dev.lambdaurora.lambdynlights.compat.SodiumCompat"

                        [[modproperties.lambdynlights."yumi:entrypoints"."yumi:client_init"]]
                        value = "dev.lambdaurora.lambdynlights.LambDynLights::INSTANCE"
                        """);
        Path provider = jar("forgified.jar", modsToml("javafml", "[1,)", "fabric_api_base", "1.0", "") + """
                        [modproperties.fabric_api_base]
                        "fabric:provides" = ["fabric-api-base-alias"]
                        """);

        ForgeModRequirements.Result result = check(List.of(lights, provider), GameSide.CLIENT);
        List<BridgeLaunchContext.LoadedMod> mods = NeoForgeModRequirements.hostMods(List.of(lights, provider));

        assertEquals(List.of(lights, provider), result.kept(), result.excluded().toString());
        assertEquals(
                List.of("lambdynlights", "fabric_api_base"),
                mods.stream().map(BridgeLaunchContext.LoadedMod::modId).toList());
        assertEquals(
                List.of("fabric-api-base-alias", "fabric-api-base"), mods.get(1).providedIds());
    }

    /** JarJar's {@code metadata.json} naming {@code paths}. */
    private static String jarJarMetadata(String... paths) {
        StringBuilder json = new StringBuilder("{\"jars\": [");
        for (int i = 0; i < paths.length; i++) {
            json.append(i == 0 ? "" : ", ")
                    .append("{\"identifier\": {\"group\": \"com.example\", \"artifact\": \"lib")
                    .append(i)
                    .append("\"}, \"version\": {\"range\": \"[1,)\", \"artifactVersion\": \"1\"}, \"path\": \"")
                    .append(paths[i])
                    .append("\", \"isObfuscated\": false}");
        }
        return json.append("]}").toString();
    }

    private static String modsToml(
            String loader, String loaderVersion, String id, String version, String dependencies) {
        return "modLoader=\"" + loader + "\"\nloaderVersion=\"" + loaderVersion
                + "\"\nlicense=\"MIT\"\n" + dependencies.replace("[[dependencies", "\n[[dependencies")
                + "\n[[mods]]\nmodId=\"" + id + "\"\nversion=\"" + version + "\"\n";
    }

    private Path jar(String name, String modsToml) throws IOException {
        return jar(name, Map.of("META-INF/neoforge.mods.toml", modsToml));
    }

    private Path jar(String name, Map<String, Object> entries) throws IOException {
        return jar(name, entries, null);
    }

    private Path jar(String name, Map<String, Object> entries, String version) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file, manifest(version))) {
            write(out, entries);
        }
        return jar;
    }

    private static byte[] nestedJar(String modsToml, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bytes, manifest(version))) {
            write(out, Map.of("META-INF/neoforge.mods.toml", modsToml));
        }
        return bytes.toByteArray();
    }

    private static Manifest manifest(String version) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (version != null) {
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        }
        return manifest;
    }

    private static void write(JarOutputStream out, Map<String, ?> entries) throws IOException {
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            out.putNextEntry(new JarEntry(entry.getKey()));
            out.write(
                    entry.getValue() instanceof byte[] bytes
                            ? bytes
                            : entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
