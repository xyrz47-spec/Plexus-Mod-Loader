package dev.polymodloader.compat.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.SharedMixinConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What FML would refuse in mods.toml is left out before it starts, with the mods that need it. */
class ForgeModRequirementsTest {

    @TempDir
    Path directory;

    private static ForgeModRequirements.VersionRanges ranges() throws ReflectiveOperationException {
        return ForgeModRequirements.maven(ForgeModRequirementsTest.class.getClassLoader());
    }

    /** The real corpus case: libIPN built for Forge 66 / Minecraft 26.3, and InventoryProfilesNext needing it. */
    @Test
    void aModForAnotherForgeVersionIsLeftOutWithTheModsThatNeedIt() throws Exception {
        Path library = jar("libipn.jar", modsToml("javafml", "[66,)", "libipn", "6.9.0", """
                [[dependencies.libipn]]
                modId="forge"
                mandatory=true
                versionRange="[66,)"
                [[dependencies.libipn]]
                modId="minecraft"
                mandatory=true
                versionRange="[26.3, 26.4)"
                """));
        Path user = jar("ipn.jar", modsToml("javafml", "[65,)", "inventoryprofilesnext", "2.3.8", """
                [[dependencies.inventoryprofilesnext]]
                modId="minecraft"
                mandatory=true
                versionRange="[26.2, 26.3)"
                [[dependencies.inventoryprofilesnext]]
                modId="libipn"
                mandatory=true
                versionRange="[6.9.0, 6.10)"
                """));
        Path fine = jar("fine.jar", modsToml("javafml", "[65,)", "fine", "1.0", """
                [[dependencies.fine]]
                modId="minecraft"
                mandatory=true
                versionRange="[26.2,26.3)"
                [[dependencies.fine]]
                modId="jei"
                mandatory=false
                versionRange="[99,)"
                """));

        ForgeModRequirements.Result result = ForgeModRequirements.check(
                List.of(library, user, fine), "26.2", "65.1.5", GameSide.DEDICATED_SERVER, ranges());

        assertEquals(List.of(fine), result.kept());
        assertEquals(2, result.excluded().size());
        String libraryReasons = String.join("; ", result.excluded().get(0).reasons());
        assertTrue(libraryReasons.contains("javafml must be [66,), Forge has 65.1.5"), libraryReasons);
        assertTrue(
                libraryReasons.contains("requires minecraft [26.3, 26.4), but Minecraft 26.2 is running"),
                libraryReasons);
        String userReasons = String.join("; ", result.excluded().get(1).reasons());
        assertTrue(
                userReasons.contains("requires libipn [6.9.0, 6.10), which is installed but NOT loaded (libipn.jar)"),
                userReasons);
    }

    @Test
    void aDependencyNestedInAnotherJarCountsAndSidesAreRespected() throws Exception {
        Path bundle = jar(
                "bundle.jar",
                Map.of(
                        "META-INF/mods.toml",
                        modsToml("javafml", "[65,)", "bundle", "1.0", ""),
                        "META-INF/jarjar/lib.jar",
                        nestedJar(modsToml("javafml", "[65,)", "nestedlib", "2.0", ""))));
        Path user = jar("user.jar", modsToml("javafml", "[65,)", "user", "1.0", """
                [[dependencies.user]]
                modId="nestedlib"
                mandatory=true
                versionRange="[2,)"
                [[dependencies.user]]
                modId="clientonly"
                mandatory=true
                versionRange="*"
                side="CLIENT"
                """));
        Path service =
                jar("service.jar", Map.of("META-INF/services/cpw.mods.modlauncher.api.ITransformationService", ""));

        ForgeModRequirements.Result server = ForgeModRequirements.check(
                List.of(bundle, user, service), "26.2", "65.1.5", GameSide.DEDICATED_SERVER, ranges());
        ForgeModRequirements.Result client =
                ForgeModRequirements.check(List.of(bundle, user, service), "26.2", "65.1.5", GameSide.CLIENT, ranges());

        assertEquals(
                List.of(bundle, user, service), server.kept(), server.excluded().toString());
        assertEquals(List.of(bundle, service), client.kept());
        assertTrue(client.excluded().getFirst().reasons().getFirst().contains("clientonly"));
    }

    /** Cubes Without Borders: its @Mod class is @OnlyIn(Dist.CLIENT), which Forge refuses on a server. */
    @Test
    void aClientOnlyModIsLeftOutOnAServerOnly() throws Exception {
        Path clientMod = jar(
                "cwb.jar",
                Map.of(
                        "META-INF/mods.toml",
                        modsToml("javafml", "*", "cwb", "4.1.0", ""),
                        "dev/example/ClientMod.class",
                        clientOnlyModClass()));

        ForgeModRequirements.Result server =
                ForgeModRequirements.check(List.of(clientMod), "26.2", "65.1.5", GameSide.DEDICATED_SERVER, ranges());
        ForgeModRequirements.Result client =
                ForgeModRequirements.check(List.of(clientMod), "26.2", "65.1.5", GameSide.CLIENT, ranges());

        assertEquals(List.of(), server.kept());
        assertTrue(server.excluded().getFirst().reasons().getFirst().contains("dev.example.ClientMod"));
        assertEquals(List.of(clientMod), client.kept());
    }

    /** BHMenu: every dependency, minecraft included, is for the CLIENT side; it reaches client classes when built. */
    @Test
    void aModNeedingMinecraftOnTheClientOnlyIsLeftOutOnAServer() throws Exception {
        Path menu = jar("bhmenu.jar", modsToml("javafml", "[65,)", "bhmenu", "3.0.0", """
                [[dependencies.bhmenu]]
                modId = "minecraft"
                mandatory = true
                versionRange = "[26.2, 26.3)"
                side = "CLIENT"
                """));

        assertEquals(
                List.of(),
                ForgeModRequirements.check(List.of(menu), "26.2", "65.1.5", GameSide.DEDICATED_SERVER, ranges())
                        .kept());
        assertEquals(
                List.of(menu),
                ForgeModRequirements.check(List.of(menu), "26.2", "65.1.5", GameSide.CLIENT, ranges())
                        .kept());
    }

    /** CustomSkinLoader: a ModLauncher service without mods.toml, client-only by its fabric.mod.json. */
    @Test
    void aServiceJarDeclaredClientOnlyByItsFabricMetadataIsLeftOutOnAServerOnly() throws Exception {
        Path skins = jar(
                "skins.jar",
                Map.of(
                        "META-INF/services/cpw.mods.modlauncher.api.ITransformationService",
                        "example.SkinService",
                        "fabric.mod.json",
                        "{\"schemaVersion\": 1, \"id\": \"skins\", \"version\": \"1\", \"environment\": \"client\"}"));
        Path broken = jar(
                "broken.jar",
                Map.of(
                        "META-INF/services/cpw.mods.modlauncher.api.ITransformationService",
                        "example.Service",
                        "fabric.mod.json",
                        "{ not json"));

        ForgeModRequirements.Result server = ForgeModRequirements.check(
                List.of(skins, broken), "26.2", "65.1.5", GameSide.DEDICATED_SERVER, ranges());
        ForgeModRequirements.Result client =
                ForgeModRequirements.check(List.of(skins, broken), "26.2", "65.1.5", GameSide.CLIENT, ranges());

        assertEquals(List.of(broken), server.kept());
        assertEquals("skins", server.excluded().getFirst().modId());
        assertTrue(server.excluded().getFirst().reasons().getFirst().contains("client-only"));
        assertEquals(List.of(skins, broken), client.kept());
    }

    /** Balm 26.1's nested kuma-api names a Mixin configuration it does not have; Mixin would stop the launch. */
    @Test
    void aModWhoseNestedJarNamesAMissingMixinConfigurationIsLeftOutWithTheModsThatNeedIt() throws Exception {
        ByteArrayOutputStream nested = new ByteArrayOutputStream();
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("MixinConfigs", "kuma.mixins.json,kuma.api.mixins.json");
        try (JarOutputStream out = new JarOutputStream(nested, manifest)) {
            write(out, Map.of("kuma.mixins.json", "{}"));
        }
        Path balm = jar(
                "balm.jar",
                Map.of(
                        "META-INF/mods.toml",
                        modsToml("javafml", "[65,)", "balm", "1.0", ""),
                        "META-INF/jarjar/kuma.jar",
                        nested.toByteArray()));
        Path user = jar("user.jar", modsToml("javafml", "[65,)", "user", "1.0", """
                [[dependencies.user]]
                modId="balm"
                mandatory=true
                versionRange="*"
                """));

        ForgeModRequirements.Result result =
                ForgeModRequirements.check(List.of(balm, user), "26.2", "65.1.5", GameSide.DEDICATED_SERVER, ranges());

        assertEquals(List.of(), result.kept());
        assertTrue(result.excluded().getFirst().reasons().getFirst().contains("kuma.api.mixins.json"));
    }

    /**
     * Mixin 0.8.7 registers only the {@code MixinConfigs} names that end in {@code .json}
     * ({@code MixinPlatformManager.addConfig}) and ignores the rest, so a missing name without it stops nothing.
     */
    @Test
    void aManifestNameMixinDoesNotRegisterIsNeitherCheckedNorApplied() throws Exception {
        Path mod = jar(
                "mod.jar",
                Map.of(
                        "META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\nMixinConfigs: mod.mixins.json, , mod.refmap\n",
                        "META-INF/mods.toml",
                        modsToml("javafml", "[65,)", "mod", "1.0", ""),
                        "mod.mixins.json",
                        "{\"compatibilityLevel\": \"JAVA_21\"}"));

        ForgeModRequirements.Result result = ForgeModRequirements.check(
                List.of(mod), "26.2", "65.1.5", GameSide.DEDICATED_SERVER, ranges(), Set.of("JAVA_17", "JAVA_21"));

        assertEquals(List.of(mod), result.kept(), result.excluded().toString());
        assertEquals(
                List.of("mod.mixins.json"),
                ForgeMixinConfigs.read(List.of(mod)).stream()
                        .map(SharedMixinConfig::config)
                        .toList());
    }

    /**
     * Boatiview and No Chat Reports for 26.1 need {@code JAVA_25}, which Mixin 0.8.7 does not know: real Forge 62.0.9
     * stops the launch ({@code MixinConfig.initCompatibilityLevel} throws {@code MixinInitialisationError}, an
     * {@code Error} that {@code Mixins.createConfiguration} does not catch). Mixin reads the level trimmed and upper-case,
     * and reads the configurations of nested jars too.
     */
    @Test
    void aMixinConfigurationNeedingAnUnknownLevelLeavesItsModOutAsMixinReadsIt() throws Exception {
        Set<String> levels = Set.of("JAVA_17", "JAVA_21");
        Path newer = mixinMod("newer.jar", "newer", "JAVA_25");
        Path lowerCase = mixinMod("lower.jar", "lower", " java_21 ");
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("MixinConfigs", "lib.mixins.json");
        ByteArrayOutputStream nested = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(nested, manifest)) {
            write(out, Map.of("lib.mixins.json", "{\"compatibilityLevel\": \"JAVA_25\"}"));
        }
        Path bundle = jar(
                "bundle.jar",
                Map.of(
                        "META-INF/mods.toml",
                        modsToml("javafml", "[62,)", "bundle", "1.0", ""),
                        "META-INF/jarjar/lib.jar",
                        nested.toByteArray()));

        ForgeModRequirements.Result result = ForgeModRequirements.check(
                List.of(newer, lowerCase, bundle), "26.1", "62.0.9", GameSide.DEDICATED_SERVER, ranges(), levels);

        assertEquals(List.of(lowerCase), result.kept(), result.excluded().toString());
        assertTrue(result.excluded().get(0).reasons().getFirst().contains("JAVA_25"));
        assertTrue(result.excluded().get(1).reasons().getFirst().contains("lib.mixins.json"));
    }

    private Path mixinMod(String name, String id, String level) throws IOException {
        return jar(
                name,
                Map.of(
                        "META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\nMixinConfigs: " + id + ".mixins.json\n",
                        "META-INF/mods.toml",
                        modsToml("javafml", "[62,)", id, "1.0", ""),
                        id + ".mixins.json",
                        "{\"compatibilityLevel\": \"" + level + "\"}"));
    }

    private static byte[] clientOnlyModClass() {
        return ClassFile.of()
                .build(
                        ClassDesc.of("dev.example.ClientMod"),
                        builder -> builder.with(RuntimeVisibleAnnotationsAttribute.of(
                                Annotation.of(
                                        ClassDesc.of("net.minecraftforge.fml.common.Mod"),
                                        AnnotationElement.ofString("value", "cwb")),
                                Annotation.of(
                                        ClassDesc.of("net.minecraftforge.api.distmarker.OnlyIn"),
                                        AnnotationElement.of(
                                                "value",
                                                AnnotationValue.ofEnum(
                                                        ClassDesc.of("net.minecraftforge.api.distmarker.Dist"),
                                                        "CLIENT"))))));
    }

    private static String modsToml(
            String loader, String loaderVersion, String id, String version, String dependencies) {
        return "modLoader=\"" + loader + "\"\nloaderVersion=\"" + loaderVersion
                + "\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"" + id + "\"\nversion=\"" + version + "\"\n" + dependencies;
    }

    private Path jar(String name, String modsToml) throws IOException {
        return jar(name, Map.of("META-INF/mods.toml", modsToml));
    }

    private Path jar(String name, Map<String, Object> entries) throws IOException {
        Path jar = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file)) {
            write(out, entries);
        }
        return jar;
    }

    private static byte[] nestedJar(String modsToml) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bytes)) {
            write(out, Map.of("META-INF/mods.toml", modsToml));
        }
        return bytes.toByteArray();
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
