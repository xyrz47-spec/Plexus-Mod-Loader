package dev.plexusmodloader.bootstrap;

import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.CompatibilityFinding;
import dev.plexusmodloader.compat.bridge.ModLoaderBridge;
import dev.plexusmodloader.runtime.compat.ModLinkVerifier;
import dev.plexusmodloader.runtime.discovery.ForeignModResolver;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Quilt Loader API as the link check sees it, with the real Quilt bridge and the real bridge jar on the class path
 * (ADR-0027 decision 4): what the jar packs and the three stand-ins resolve; Quilt Loader classes it does not pack, and
 * stand-in members it lacks, block the mod with {@code PLEXUS-COMPAT-111}. Here rather than in {@code poly-runtime-impl},
 * whose tests do not have a loader bridge on their class path.
 */
class QuiltLoaderApiLinkTest {

    private static final ClassDesc QUILT_LOADER_IMPL = ClassDesc.of("org.quiltmc.loader.impl.QuiltLoaderImpl");

    @TempDir
    Path directory;

    private ModLoaderBridge quilt;
    private Path gameJar;

    @BeforeEach
    void findTheBridge() throws IOException {
        quilt = new ForeignModResolver(getClass().getClassLoader())
                .bridges().stream()
                        .filter(bridge -> bridge.loaderName().equals("Quilt"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("the Quilt bridge is not on the class path"));
        gameJar = jar(
                "game.jar",
                Map.of(
                        "net/minecraft/world/Base",
                        ClassFile.of().build(ClassDesc.of("net.minecraft.world.Base"), cb -> {})));
    }

    private Path jar(String name, Map<String, byte[]> classes) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(path);
                JarOutputStream jar = new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return path;
    }

    /** A Quilt mod whose one method runs {@code body}. */
    private Path mod(String name, Consumer<CodeBuilder> body) throws IOException {
        ClassDesc type = ClassDesc.of("com.example." + name + ".Entry");
        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                .build(
                        type,
                        cb -> cb.withMethodBody(
                                "run",
                                MethodTypeDesc.of(CD_void),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                code -> {
                                    body.accept(code);
                                    code.return_();
                                }));
        return jar(name + ".jar", Map.of("com/example/" + name + "/Entry", bytes));
    }

    private List<CompatibilityFinding> verify(Path mod) {
        try (ModLinkVerifier verifier =
                new ModLinkVerifier(List.of(gameJar), getClass().getClassLoader(), GameSide.DEDICATED_SERVER)) {
            return verifier.verify(mod, List.of(), quilt, List.of());
        }
    }

    private static List<String> blocking(List<CompatibilityFinding> findings) {
        return findings.stream()
                .filter(CompatibilityFinding::blocking)
                .map(CompatibilityFinding::code)
                .toList();
    }

    @Test
    void thePackedApiTheStandInsAndFabricsApiResolve() throws IOException {
        Path mod = mod("quilt_user", code -> {
            code.ldc("fabric_lib");
            code.invokestatic(
                    ClassDesc.of("org.quiltmc.loader.api.QuiltLoader"),
                    "isModLoaded",
                    MethodTypeDesc.of(CD_boolean, CD_String));
            code.pop();
            code.getstatic(QUILT_LOADER_IMPL, "INSTANCE", QUILT_LOADER_IMPL);
            code.ldc("fabric_lib");
            code.invokevirtual(QUILT_LOADER_IMPL, "isModLoaded", MethodTypeDesc.of(CD_boolean, CD_String));
            code.pop();
            code.ldc(ClassDesc.of("org.quiltmc.loader.api.minecraft.ClientOnly"));
            code.ldc(ClassDesc.of("org.quiltmc.loader.impl.util.DefaultLanguageAdapter"));
            code.ldc(ClassDesc.of("org.quiltmc.loader.impl.game.GameProvider"));
            code.ldc(ClassDesc.of("net.fabricmc.loader.api.FabricLoader"));
        });

        assertEquals(List.of(), verify(mod));
    }

    /**
     * Quilt's virtual file systems and its loader plugin API are not packed: they lead into the loader. Quilt Loader's
     * internal classes are not offered to mods either, even the helpers the packed API uses (as in Fabric's bridge).
     */
    @Test
    void quiltLoaderClassesTheBridgeDoesNotPackBlock() throws IOException {
        Path fileSystems = mod("fs_user", code -> code.ldc(ClassDesc.of("org.quiltmc.loader.api.QuiltFileSystems")));
        Path plugin =
                mod("plugin_user", code -> code.ldc(ClassDesc.of("org.quiltmc.loader.api.plugin.QuiltLoaderPlugin")));
        Path internal =
                mod("internal_user", code -> code.ldc(ClassDesc.of("org.quiltmc.loader.impl.util.SystemProperties")));

        Path packedHelper = mod(
                "helper_user",
                code -> code.ldc(ClassDesc.of("org.quiltmc.loader.impl.metadata.qmj.SemanticVersionImpl")));

        for (Path mod : List.of(fileSystems, plugin, internal, packedHelper)) {
            List<CompatibilityFinding> findings = verify(mod);
            assertEquals(List.of("PLEXUS-COMPAT-111"), blocking(findings), findings::toString);
        }
        assertTrue(verify(fileSystems).toString().contains("org.quiltmc.loader.api.QuiltFileSystems"));
    }

    /** A member of Quilt Loader's QuiltLoaderImpl that the stand-in does not have fails as it is used. */
    @Test
    void aQuiltLoaderImplMemberTheStandInLacksBlocks() throws IOException {
        Path mod = mod("mods_dir_user", code -> {
            code.getstatic(QUILT_LOADER_IMPL, "INSTANCE", QUILT_LOADER_IMPL);
            code.invokevirtual(QUILT_LOADER_IMPL, "getModsDir", MethodTypeDesc.of(ClassDesc.of("java.nio.file.Path")));
            code.pop();
        });

        List<CompatibilityFinding> findings = verify(mod);

        assertEquals(List.of("PLEXUS-COMPAT-111"), blocking(findings));
        assertTrue(findings.toString().contains("getModsDir"), findings::toString);
    }

    /**
     * The bridge jar packs Quilt Loader's public API, and neither Quilt Loader's file systems nor its own copy of
     * Fabric Loader's classes: Fabric's come from Fabric Loader 0.19.5.
     */
    @Test
    void theBridgeJarPacksQuiltsApiOnly() throws IOException, URISyntaxException {
        Path bridgeJar = Path.of(quilt.getClass()
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        assertTrue(bridgeJar.toString().endsWith(".jar"), "the bridge is used as its jar: " + bridgeJar);
        try (JarFile jar = new JarFile(bridgeJar.toFile())) {
            assertNotNull(jar.getEntry("org/quiltmc/loader/api/QuiltLoader.class"));
            assertNotNull(jar.getEntry("org/quiltmc/loader/api/minecraft/MinecraftQuiltLoader.class"));
            assertNotNull(jar.getEntry("org/quiltmc/loader/impl/QuiltLoaderImpl.class"), "the stand-in");
            assertNotNull(jar.getEntry("LICENSE_quilt-loader"));
            assertNotNull(jar.getEntry("quilt_loader/licenses.json"));
            assertFalse(
                    jar.stream().anyMatch(entry -> entry.getName().startsWith("org/quiltmc/loader/impl/filesystem/")),
                    "Quilt Loader's file systems are not packed");
            assertFalse(
                    jar.stream().anyMatch(entry -> entry.getName().startsWith("org/quiltmc/loader/api/plugin/")),
                    "Quilt Loader's plugin API is not packed");
            // Quilt Loader's jar carries its own copies of Fabric Loader classes; only Fabric Loader's API is packed.
            assertNotNull(jar.getEntry("net/fabricmc/loader/api/FabricLoader.class"));
            for (String quiltCopy : List.of(
                    "net/fabricmc/loader/launch/",
                    "net/fabricmc/loader/impl/gui/",
                    "net/fabricmc/loader/impl/launch/knot/")) {
                assertFalse(
                        jar.stream().anyMatch(entry -> entry.getName().startsWith(quiltCopy)),
                        "Quilt Loader's copy of " + quiltCopy + " is not packed");
            }
        }
    }
}
