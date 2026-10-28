package dev.polymodloader.runtime.compat;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.BridgedAccessWidening;
import dev.polymodloader.compat.bridge.BridgedAccessWidening.Access;
import dev.polymodloader.compat.bridge.BridgedAccessWidening.Kind;
import dev.polymodloader.compat.bridge.BridgedEntrypoint;
import dev.polymodloader.compat.bridge.BridgedInterfaceInjection;
import dev.polymodloader.compat.bridge.CompatibilityFinding;
import dev.polymodloader.compat.bridge.ModLoaderBridge;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModLinkVerifierTest {

    private static final ClassDesc BASE = ClassDesc.of("net.minecraft.world.Base");
    private static final ClassDesc CHILD = ClassDesc.of("net.minecraft.world.Child");
    private static final ModLoaderBridge FABRIC_LIKE = new ModLoaderBridge() {
        @Override
        public String loaderName() {
            return "Fabric";
        }

        @Override
        public String metadataPath() {
            return "fabric.mod.json";
        }

        @Override
        public BridgeAssessment assess(String metadata, GameSide side) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) {}

        @Override
        public List<String> runtimePackages() {
            return List.of("net/fabricmc/");
        }

        @Override
        public List<String> providedPackages() {
            return List.of("net/fabricmc/api/");
        }
    };

    @TempDir
    Path directory;

    private Path gameJar;

    /** A game with {@code Base.tick()V}, {@code Base.count I} and {@code Child extends Base}. */
    @BeforeEach
    void writeGame() throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put("net/minecraft/world/Base", ClassFile.of().build(BASE, base -> {
            base.withField("count", CD_int, ClassFile.ACC_PUBLIC);
            base.withMethodBody("tick", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC, CodeBuilder::return_);
        }));
        classes.put("net/minecraft/world/Child", ClassFile.of().build(CHILD, child -> child.withSuperclass(BASE)));
        gameJar = jar("game.jar", classes);
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

    /** A mod class whose one method runs {@code body}. */
    private Path mod(String name, Consumer<CodeBuilder> body) throws IOException {
        ClassDesc type = ClassDesc.of("com.example." + name + ".Entry");
        Consumer<ClassBuilder> build = cb -> cb.withMethodBody(
                "run", MethodTypeDesc.of(CD_void), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    body.accept(code);
                    code.return_();
                });
        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).build(type, build);
        return jar(name + ".jar", Map.of("com/example/" + name + "/Entry", bytes));
    }

    private List<CompatibilityFinding> verify(Path mod, GameSide side, List<Path> others, List<String> entrypoints) {
        return verify(mod, side, others, entrypoints, FABRIC_LIKE);
    }

    private List<CompatibilityFinding> verify(
            Path mod, GameSide side, List<Path> others, List<String> entrypoints, ModLoaderBridge bridge) {
        try (ModLinkVerifier verifier =
                new ModLinkVerifier(List.of(gameJar), getClass().getClassLoader(), side)) {
            return verifier.verify(mod, others, bridge, entrypoints);
        }
    }

    private static List<String> blockingCodes(List<CompatibilityFinding> findings) {
        return findings.stream()
                .filter(CompatibilityFinding::blocking)
                .map(CompatibilityFinding::code)
                .toList();
    }

    @Test
    void inheritedGameMembersAndJdkMembersResolve() throws IOException {
        Path mod = mod("fine", code -> {
            code.new_(CHILD);
            code.invokevirtual(CHILD, "tick", MethodTypeDesc.of(CD_void));
            code.getstatic(CHILD, "count", CD_int);
            code.invokevirtual(CHILD, "toString", MethodTypeDesc.of(CD_String));
            code.invokestatic(ClassDesc.of("java.lang.String"), "valueOf", MethodTypeDesc.of(CD_String, CD_Object));
            code.ldc(ClassDesc.of("net.fabricmc.api.ModInitializer"));
        });
        // The provided part of the loader runtime is on Poly Mod Loader's class path, as in a real launch.
        Path fabricApi = jar(
                "fabric-api-interfaces.jar",
                Map.of(
                        "net/fabricmc/api/ModInitializer",
                        ClassFile.of()
                                .build(
                                        ClassDesc.of("net.fabricmc.api.ModInitializer"),
                                        cb -> cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE))));

        try (URLClassLoader platform = new URLClassLoader(
                        new URL[] {fabricApi.toUri().toURL()}, getClass().getClassLoader());
                ModLinkVerifier verifier = new ModLinkVerifier(List.of(gameJar), platform, GameSide.CLIENT)) {
            assertEquals(List.of(), verifier.verify(mod, List.of(), FABRIC_LIKE, List.of("com.example.fine.Entry")));
        }
    }

    @Test
    void membersAndClassesThisGameLacksBlock() throws IOException {
        Path mod = mod("stale", code -> {
            code.invokevirtual(CHILD, "removed", MethodTypeDesc.of(CD_void));
            code.getstatic(BASE, "count", CD_String);
            code.ldc(ClassDesc.of("net.minecraft.world.Gone"));
        });

        List<CompatibilityFinding> findings = verify(mod, GameSide.CLIENT, List.of(), List.of());

        assertEquals(List.of("POLY-COMPAT-112", "POLY-COMPAT-112"), blockingCodes(findings));
        String all = findings.toString();
        assertTrue(all.contains("net.minecraft.world.Child#removed()V"), all);
        assertTrue(all.contains("net.minecraft.world.Base.countLjava/lang/String;"), all);
        assertTrue(all.contains("net.minecraft.world.Gone"), all);
    }

    /**
     * A class of another loader's code path (it uses NeoForge's API, which nothing installed has) fails as it loads, so
     * Minecraft members it uses that this game lacks, such as methods NeoForge patches in, only note, as collective's
     * NeoForge network handler does on Fabric. The same member in other code still blocks.
     */
    @Test
    void membersUsedByAnotherLoadersCodePathOnlyNote() throws IOException {
        Path neoForgePath = mod("multi_loader", code -> {
            code.ldc(ClassDesc.of("net.neoforged.neoforge.network.handling.IPayloadContext"));
            code.invokevirtual(CHILD, "removed", MethodTypeDesc.of(CD_void));
        });
        Path plain = mod("stale", code -> code.invokevirtual(CHILD, "removed", MethodTypeDesc.of(CD_void)));

        List<CompatibilityFinding> findings = verify(neoForgePath, GameSide.CLIENT, List.of(), List.of());

        assertEquals(List.of(), blockingCodes(findings));
        assertTrue(findings.toString().contains("net.minecraft.world.Child#removed()V"), findings::toString);
        assertEquals(List.of("POLY-COMPAT-112"), blockingCodes(verify(plain, GameSide.CLIENT, List.of(), List.of())));
    }

    /**
     * A runtime class Poly Mod Loader provides only in part (a stand-in of a loader's internal class): members it has
     * resolve, members it lacks block the mod, as they would fail with {@link NoSuchMethodError}.
     */
    @Test
    void membersMissingFromAPartialRuntimeClassBlock() throws IOException {
        ClassDesc partial = ClassDesc.of(PartialRuntime.class.getName());
        ModLoaderBridge bridge = new ModLoaderBridge() {
            @Override
            public String loaderName() {
                return "Fabric";
            }

            @Override
            public String metadataPath() {
                return "fabric.mod.json";
            }

            @Override
            public BridgeAssessment assess(String metadata, GameSide side) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void invokeEntrypoint(BridgedEntrypoint entrypoint, ClassLoader gameClassLoader) {}

            @Override
            public List<String> runtimePackages() {
                return List.of(PartialRuntime.class.getName().replace('.', '/'));
            }

            @Override
            public List<String> providedPackages() {
                return runtimePackages();
            }
        };
        Path known = mod("known", code -> code.invokestatic(partial, "known", MethodTypeDesc.of(CD_String)));
        Path unknown = mod("unknown", code -> code.invokestatic(partial, "unknown", MethodTypeDesc.of(CD_String)));

        assertEquals(List.of(), verify(known, GameSide.CLIENT, List.of(), List.of(), bridge));
        List<CompatibilityFinding> findings = verify(unknown, GameSide.CLIENT, List.of(), List.of(), bridge);
        assertEquals(List.of("POLY-COMPAT-111"), blockingCodes(findings));
        assertTrue(findings.toString().contains("PartialRuntime#unknown()Ljava/lang/String;"), findings::toString);
    }

    /** Stands in for a loader's internal class that Poly Mod Loader provides only in part. */
    static final class PartialRuntime {
        private PartialRuntime() {}

        static String known() {
            return "known";
        }
    }

    /** LaunchWrapper was a library of the old launchers, not game classes; multi-version mods reference it. */
    @Test
    void launchWrapperIsALibraryNotAGameClass() throws IOException {
        Path mod =
                mod("multi_version", code -> code.ldc(ClassDesc.of("net.minecraft.launchwrapper.LaunchClassLoader")));

        List<CompatibilityFinding> findings = verify(mod, GameSide.CLIENT, List.of(), List.of());

        assertEquals(List.of(), blockingCodes(findings));
        assertTrue(findings.toString().contains("POLY-COMPAT-113"), findings::toString);
    }

    @Test
    void theLoadersOwnRuntimeBlocksUnlessProvided() throws IOException {
        Path mod = mod("loader_user", code -> code.ldc(ClassDesc.of("net.fabricmc.loader.api.FabricLoader")));

        List<CompatibilityFinding> findings = verify(mod, GameSide.CLIENT, List.of(), List.of());

        assertEquals(List.of("POLY-COMPAT-111"), blockingCodes(findings));
        assertTrue(findings.getFirst().detail().contains("net.fabricmc.loader.api.FabricLoader"));
    }

    @Test
    void classesOfAnotherInstalledModResolve() throws IOException {
        Path library = jar(
                "library.jar",
                Map.of(
                        "net/fabricmc/fabric/api/Event",
                        ClassFile.of().build(ClassDesc.of("net.fabricmc.fabric.api.Event"), cb -> {})));
        Path mod = mod("api_user", code -> code.ldc(ClassDesc.of("net.fabricmc.fabric.api.Event")));

        assertEquals(List.of(), verify(mod, GameSide.CLIENT, List.of(library), List.of()));
        assertEquals(List.of("POLY-COMPAT-111"), blockingCodes(verify(mod, GameSide.CLIENT, List.of(), List.of())));
    }

    @Test
    void optionalIntegrationsAndClientClassesOnAServerOnlyNote() throws IOException {
        Path mod = mod("integrations", code -> {
            code.ldc(ClassDesc.of("com.othermod.api.Hook"));
            code.ldc(ClassDesc.of("net.minecraft.client.Minecraft"));
        });

        List<CompatibilityFinding> server = verify(mod, GameSide.DEDICATED_SERVER, List.of(), List.of());
        List<CompatibilityFinding> client = verify(mod, GameSide.CLIENT, List.of(), List.of());

        assertEquals(List.of(), blockingCodes(server));
        assertEquals(2, server.size(), server::toString);
        assertEquals(List.of("POLY-COMPAT-112"), blockingCodes(client), "this fake game has no client classes");
    }

    /**
     * Minecraft 26.3 renamed Blaze3D to {@code com.mojang.renderpearl}, which only the client jar has. Before that was a
     * client package here, nine Fabric mods of the 26.3 corpus (AppleSkin, Cloth Config, GeckoLib, YACL, ...) were
     * left out on the server with POLY-COMPAT-112, and 18 more with them (corpusFabric 26.3, 2026-10-27).
     */
    @Test
    void renderPearlIsAClientPackage() throws IOException {
        Path mod = mod("renderpearl", code -> code.ldc(ClassDesc.of("com.mojang.renderpearl.api.buffers.GpuBuffer")));

        List<CompatibilityFinding> server = verify(mod, GameSide.DEDICATED_SERVER, List.of(), List.of());

        assertEquals(List.of(), blockingCodes(server), server::toString);
    }

    @Test
    void aMissingEntrypointClassBlocks() throws IOException {
        Path mod = mod("typo", code -> {});

        assertEquals(
                List.of("POLY-COMPAT-119"),
                blockingCodes(verify(mod, GameSide.CLIENT, List.of(), List.of("com.example.typo.Entyr"))));
    }

    @Test
    void jdkClassesThisRuntimeLacksBlock() throws IOException {
        Path mod = mod("future_java", code -> code.ldc(ClassDesc.of("java.lang.FutureFeature")));

        assertEquals(List.of("POLY-COMPAT-118"), blockingCodes(verify(mod, GameSide.CLIENT, List.of(), List.of())));
    }

    private static BridgedAccessWidening widening(String owner, Kind kind, String name, String descriptor, int line) {
        return new BridgedAccessWidening(
                "mod.accesswidener:" + line, owner, kind, name, descriptor, Set.of(Access.ACCESSIBLE));
    }

    private List<CompatibilityFinding> verifyWidenings(GameSide side, List<BridgedAccessWidening> widenings)
            throws IOException {
        Path mod = mod("widening_user", code -> {});
        try (ModLinkVerifier verifier =
                new ModLinkVerifier(List.of(gameJar), getClass().getClassLoader(), side)) {
            return verifier.verify(mod, List.of(), FABRIC_LIKE, List.of(), widenings);
        }
    }

    @Test
    void accessWideningsOfMembersTheGameDeclaresPass() throws IOException {
        assertEquals(
                List.of(),
                verifyWidenings(
                        GameSide.CLIENT,
                        List.of(
                                widening("net/minecraft/world/Base", Kind.CLASS, "", "", 2),
                                widening("net/minecraft/world/Base", Kind.FIELD, "count", "I", 3),
                                widening("net/minecraft/world/Base", Kind.METHOD, "tick", "()V", 4))));
    }

    /**
     * The planner widens only members a class declares itself, so an inherited one does not count. Targets the game
     * lacks are only noted (POLY-COMPAT-137), as Fabric Loader applies such lines to nothing; a class outside the game
     * jar still blocks (POLY-COMPAT-128).
     */
    @Test
    void accessWideningTargetsThisGameLacksAreNotedWithTheirLines() throws IOException {
        List<CompatibilityFinding> findings = verifyWidenings(
                GameSide.CLIENT,
                List.of(
                        widening("net/minecraft/world/Gone", Kind.CLASS, "", "", 2),
                        widening("net/minecraft/world/Base", Kind.FIELD, "count", "J", 3),
                        widening("net/minecraft/world/Child", Kind.METHOD, "tick", "()V", 4),
                        widening("java/lang/String", Kind.FIELD, "value", "[B", 5)));

        assertEquals(List.of("POLY-COMPAT-128"), blockingCodes(findings));
        assertTrue(
                findings.stream().anyMatch(finding -> finding.code().equals("POLY-COMPAT-137") && !finding.blocking()));
        String all = findings.toString();
        assertTrue(all.contains("class net/minecraft/world/Gone (mod.accesswidener:2)"), all);
        assertTrue(all.contains("field net/minecraft/world/Base.count:J (mod.accesswidener:3)"), all);
        assertTrue(all.contains("method net/minecraft/world/Child.tick()V (mod.accesswidener:4)"), all);
        assertTrue(all.contains("outside the game jar"), all);
        assertTrue(all.contains("field java/lang/String.value:[B (mod.accesswidener:5)"), all);
    }

    @Test
    void accessWideningsOfClientClassesOnlyNoteOnAServer() throws IOException {
        List<BridgedAccessWidening> widenings =
                List.of(widening("net/minecraft/client/Minecraft", Kind.FIELD, "instance", "I", 2));

        List<CompatibilityFinding> server = verifyWidenings(GameSide.DEDICATED_SERVER, widenings);

        assertEquals(List.of(), blockingCodes(server));
        assertEquals("POLY-COMPAT-130", server.getFirst().code());
        List<CompatibilityFinding> client = verifyWidenings(GameSide.CLIENT, widenings);
        assertEquals(List.of(), blockingCodes(client));
        assertEquals("POLY-COMPAT-137", client.getFirst().code());
    }

    /**
     * A mod calls a default method that an injected interface adds to a game class, through the game class, as
     * Loom-compiled code does (ADR-0020): it resolves. An injection whose interface no jar has blocks
     * (POLY-COMPAT-135); one into a class the game lacks is only noted (POLY-COMPAT-137).
     */
    @Test
    void injectedInterfacesCountAsSupertypes() throws IOException {
        ClassDesc api = ClassDesc.of("com.example.injector.Api");
        byte[] apiClass = ClassFile.of().build(api, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.withMethodBody("greet", MethodTypeDesc.of(CD_String), ClassFile.ACC_PUBLIC, code -> {
                code.ldc("hi");
                code.areturn();
            });
        });
        Path injector = jar("injector.jar", Map.of("com/example/injector/Api", apiClass));
        Path caller = mod("caller", code -> {
            code.aconst_null();
            code.invokevirtual(CHILD, "greet", MethodTypeDesc.of(CD_String));
            code.pop();
        });
        BridgedInterfaceInjection injection =
                new BridgedInterfaceInjection("injector.ct:2", "net/minecraft/world/Base", "com/example/injector/Api");

        try (ModLinkVerifier verifier =
                new ModLinkVerifier(List.of(gameJar), getClass().getClassLoader(), GameSide.CLIENT)) {
            assertEquals(
                    List.of("POLY-COMPAT-112"),
                    blockingCodes(verifier.verify(caller, List.of(injector), FABRIC_LIKE, List.of())),
                    "without the injection the call does not resolve");
        }
        try (ModLinkVerifier verifier =
                new ModLinkVerifier(List.of(gameJar), getClass().getClassLoader(), GameSide.CLIENT)) {
            verifier.injectInterfaces(List.of(injection));
            assertEquals(List.of(), blockingCodes(verifier.verify(caller, List.of(injector), FABRIC_LIKE, List.of())));
            assertEquals(
                    List.of(),
                    verifier.verify(injector, List.of(caller), FABRIC_LIKE, List.of(), List.of(), List.of(injection)));
            List<CompatibilityFinding> broken = verifier.verify(
                    injector,
                    List.of(),
                    FABRIC_LIKE,
                    List.of(),
                    List.of(),
                    List.of(
                            new BridgedInterfaceInjection(
                                    "injector.ct:3", "net/minecraft/world/Base", "com/example/Missing"),
                            new BridgedInterfaceInjection(
                                    "injector.ct:4", "net/minecraft/world/Gone", "com/example/injector/Api")));
            assertEquals(List.of("POLY-COMPAT-135"), blockingCodes(broken));
            assertTrue(
                    broken.stream().anyMatch(finding -> finding.code().equals("POLY-COMPAT-137")), broken.toString());
        }
    }

    private static final ClassDesc CONFIG_TYPE = ClassDesc.of("net.neoforged.fml.config.ModConfig$Type");

    /** {@code ModConfig$Type} with one static field of its own type per name, as an enum's constants are. */
    private static byte[] configType(String... constants) {
        return ClassFile.of().build(CONFIG_TYPE, cb -> {
            for (String constant : constants) {
                cb.withField(constant, CONFIG_TYPE, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }
        });
    }

    /**
     * Forge Config API Port's Fabric build carries its own copy of FML's config classes (FML 11's {@code ModConfig$Type}
     * with {@code SERVER}). Next to NeoForge, FML's module owns {@code net.neoforged.fml.config}, so the game class loader
     * defines FML's class of that name, never the mod's: on FML 12, whose {@code ModConfig$Type} has {@code SYNCED}
     * instead, the mod failed in game with {@code NoSuchFieldError} ({@code corpusFabricOnNeoForge}, 26.3). A reference
     * to a bundled class of a package the host's loader owns is checked against the host's class and blocks; the bundled
     * copies themselves are never defined, so their own references are not checked.
     */
    @Test
    void bundledCopiesOfTheHostLoadersClassesAreCheckedAgainstTheHostsClasses() throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(
                "com/example/port/Entry",
                ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                        .build(
                                ClassDesc.of("com.example.port.Entry"),
                                cb -> cb.withMethodBody(
                                        "run",
                                        MethodTypeDesc.of(CD_void),
                                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                        code -> {
                                            code.getstatic(CONFIG_TYPE, "SERVER", CONFIG_TYPE);
                                            code.getstatic(CONFIG_TYPE, "CLIENT", CONFIG_TYPE);
                                            code.ldc(ClassDesc.of("net.neoforged.fml.config.ConfigTracker"));
                                            code.return_();
                                        })));
        classes.put("net/neoforged/fml/config/ModConfig$Type", configType("COMMON", "CLIENT", "SERVER", "STARTUP"));
        // A bundled class that refers to a member only the bundled copy has: it is never defined from the mod's jar.
        classes.put(
                "net/neoforged/fml/config/ModConfigs",
                ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                        .build(
                                ClassDesc.of("net.neoforged.fml.config.ModConfigs"),
                                cb -> cb.withMethodBody(
                                        "common",
                                        MethodTypeDesc.of(CD_void),
                                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                        code -> {
                                            code.getstatic(CONFIG_TYPE, "COMMON", CONFIG_TYPE);
                                            code.return_();
                                        })));
        classes.put(
                "net/neoforged/fml/config/ConfigTracker",
                ClassFile.of().build(ClassDesc.of("net.neoforged.fml.config.ConfigTracker"), cb -> {}));
        Path mod = jar("config_port.jar", classes);
        Path fml12 = jar(
                "loader-12.jar",
                Map.of(
                        "net/neoforged/fml/config/ModConfig$Type",
                        configType("LOCAL", "CLIENT", "SYNCED", "STARTUP"),
                        "net/neoforged/fml/config/ModConfigs",
                        ClassFile.of().build(ClassDesc.of("net.neoforged.fml.config.ModConfigs"), cb -> {})));
        Path fml11 = jar(
                "loader-11.jar",
                Map.of(
                        "net/neoforged/fml/config/ModConfig$Type",
                        configType("COMMON", "CLIENT", "SERVER", "STARTUP"),
                        "net/neoforged/fml/config/ConfigTracker",
                        ClassFile.of().build(ClassDesc.of("net.neoforged.fml.config.ConfigTracker"), cb -> {})));

        // Without a host, the mod's own copies are the classes it runs with.
        assertEquals(List.of(), blockingCodes(verify(mod, GameSide.DEDICATED_SERVER, List.of(), List.of())));

        try (ModLinkVerifier verifier =
                new ModLinkVerifier(List.of(gameJar), getClass().getClassLoader(), GameSide.DEDICATED_SERVER)) {
            verifier.hostLoaderJars(List.of(fml12));
            List<CompatibilityFinding> findings = verifier.verify(mod, List.of(), FABRIC_LIKE, List.of());

            assertEquals(List.of("POLY-COMPAT-141", "POLY-COMPAT-141"), blockingCodes(findings));
            String all = findings.toString();
            assertTrue(all.contains("net.neoforged.fml.config.ModConfig$Type.SERVER"), all);
            assertTrue(all.contains("net.neoforged.fml.config.ConfigTracker"), all);
            assertTrue(!all.contains("CLIENT") && !all.contains("COMMON"), all);
        }
        // A host whose classes have what the mod uses (FML 11) lets it load.
        try (ModLinkVerifier verifier =
                new ModLinkVerifier(List.of(gameJar), getClass().getClassLoader(), GameSide.DEDICATED_SERVER)) {
            verifier.hostLoaderJars(List.of(fml11));
            assertEquals(List.of(), verifier.verify(mod, List.of(), FABRIC_LIKE, List.of()));
        }
    }
}
