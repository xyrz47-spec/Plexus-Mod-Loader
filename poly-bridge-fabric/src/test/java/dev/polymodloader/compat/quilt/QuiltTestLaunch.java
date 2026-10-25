package dev.polymodloader.compat.quilt;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeLaunchContext;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.fabricmc.api.ModInitializer;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * A launch with one mod of each kind, for the Quilt Loader API tests: the Quilt mod {@code quilt_hello}, the Fabric mod
 * {@code fabric_lib} (with the nested Fabric mod {@code fabric_inner}), the Poly Mod {@code native_lib} and the mod
 * {@code forge_mod} a host loader runs.
 */
final class QuiltTestLaunch {

    /** What the entrypoints below did, in order. */
    static final List<String> CALLS = Collections.synchronizedList(new ArrayList<>());

    /** A Quilt {@code pre_launch} entrypoint. */
    public static final class QuiltPreLaunch implements PreLaunchEntrypoint {
        @Override
        public void onPreLaunch(ModContainer mod) {
            CALLS.add("pre_launch " + mod.metadata().id());
        }
    }

    /** A Fabric {@code preLaunch} entrypoint. */
    public static final class FabricPreLaunch implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint {
        @Override
        public void onPreLaunch() {
            CALLS.add("preLaunch");
        }
    }

    /** A {@code main} entrypoint. */
    public static final class Main implements ModInitializer {
        @Override
        public void onInitialize() {
            CALLS.add("main");
        }
    }

    /** An entrypoint of a mod's own key. */
    public static final class Greeting implements Supplier<String> {
        /** For {@code Greeting::INSTANCE}. */
        public static final Greeting INSTANCE = new Greeting();

        @Override
        public String get() {
            return "hello";
        }
    }

    static final String MARKER = "com.example.quilt_hello.Marker";

    static final String QUILT_MOD_JSON = """
            {"schema_version": 1,
             "quilt_loader": {
               "group": "dev.example", "id": "quilt_hello", "version": "1.0.0",
               "intermediate_mappings": "net.fabricmc:intermediary",
               "provides": ["quilt_hello_api", {"id": "quilt_hello_versioned", "version": "3.0.0"}],
               "entrypoints": {
                 "main": "%1$s$Main",
                 "pre_launch": "%1$s$QuiltPreLaunch",
                 "preLaunch": "%1$s$FabricPreLaunch",
                 "quilt_hello:greeting": "%1$s$Greeting"},
               "depends": [
                 {"id": "minecraft", "versions": ">=26.1"},
                 [{"id": "absent_a"}, "fabric_lib"],
                 {"id": "optional_mod", "optional": true}],
               "breaks": [{"id": "bad_mod", "versions": "<2.0.0", "reason": "crashes"}],
               "metadata": {
                 "name": "Quilt Hello", "description": "Says hello",
                 "contributors": {"Ana": "Owner", "Bo": ["Artist", "Tester"]},
                 "contact": {"homepage": "https://example.com"},
                 "license": "MIT",
                 "icon": {"16": "assets/quilt_hello/small.png", "64": "assets/quilt_hello/big.png"}}},
             "mixin": [{"config": "quilt_hello.mixins.json", "environment": "*"},
                       {"config": "quilt_hello.client.mixins.json", "environment": "client"}],
             "access_widener": ["quilt_hello.accesswidener", "quilt_hello_extra.accesswidener"],
             "minecraft": {"environment": "*"},
             "quilt_hello:settings": {"answer": 42, "tags": ["a", "b"], "on": true, "none": null}}
            """.formatted(QuiltTestLaunch.class.getName());

    static final String FABRIC_MOD_JSON = """
            {"schemaVersion": 1, "id": "fabric_lib", "version": "1.2.0", "name": "Fabric Lib",
             "authors": ["Cy"], "contributors": ["Di"], "license": "Apache-2.0",
             "depends": {"minecraft": "~26.2"},
             "custom": {"fabric_lib:flag": "on"},
             "entrypoints": {"pre_launch": ["%1$s$QuiltPreLaunch"], "preLaunch": ["%1$s$FabricPreLaunch"]}}
            """.formatted(QuiltTestLaunch.class.getName());

    final Path directory;
    final Path quiltJar;
    final Path fabricJar;
    final Path innerJar;
    final Path nativeJar;
    final Path forgeJar;
    final BridgeLaunchContext context;

    QuiltTestLaunch(Path directory, GameSide side) throws IOException {
        this.directory = directory;
        Map<String, byte[]> quilt = new LinkedHashMap<>();
        quilt.put("quilt.mod.json", QUILT_MOD_JSON.getBytes(StandardCharsets.UTF_8));
        quilt.put(
                MARKER.replace('.', '/') + ".class",
                ClassFile.of().build(ClassDesc.of(MARKER), cb -> cb.withFlags(ClassFile.ACC_PUBLIC)));
        quilt.put("assets/quilt_hello/hello.txt", "hi".getBytes(StandardCharsets.UTF_8));
        quiltJar = jar("quilt-hello.jar", quilt);
        fabricJar = jar("fabric-lib.jar", Map.of("fabric.mod.json", FABRIC_MOD_JSON.getBytes(StandardCharsets.UTF_8)));
        innerJar = jar(
                "fabric-inner.jar",
                Map.of(
                        "fabric.mod.json",
                        "{\"schemaVersion\": 1, \"id\": \"fabric_inner\", \"version\": \"0.3.0\"}"
                                .getBytes(StandardCharsets.UTF_8)));
        nativeJar = jar("native-lib.jar", Map.of("poly.mod.json", "{}".getBytes(StandardCharsets.UTF_8)));
        forgeJar = jar("forge-mod.jar", Map.of("META-INF/mods.toml", "".getBytes(StandardCharsets.UTF_8)));
        context = new BridgeLaunchContext(
                side,
                "26.2",
                directory.resolve("game.jar"),
                directory.resolve("game"),
                List.of("--universe", "u", "--accessToken", "secret"),
                QuiltTestLaunch.class.getClassLoader(),
                List.of(
                        new BridgeLaunchContext.LoadedMod(
                                "MinecraftForge",
                                "forge_mod",
                                "2.0.0",
                                "Forge Mod",
                                List.of(),
                                forgeJar,
                                null,
                                null,
                                true),
                        new BridgeLaunchContext.LoadedMod(
                                "Fabric",
                                "fabric_inner",
                                "0.3.0",
                                "fabric_inner",
                                List.of(),
                                innerJar,
                                fabricJar,
                                "META-INF/jars/fabric-inner.jar"),
                        new BridgeLaunchContext.LoadedMod(
                                "Fabric", "fabric_lib", "1.2.0", "fabric_lib", List.of(), fabricJar, null, null),
                        new BridgeLaunchContext.LoadedMod(
                                "Quilt",
                                "quilt_hello",
                                "1.0.0",
                                "Quilt Hello",
                                List.of("quilt_hello_api", "quilt_hello_versioned"),
                                quiltJar,
                                null,
                                null),
                        new BridgeLaunchContext.LoadedMod(
                                "Poly Mod Loader",
                                "native_lib",
                                "2.1.0",
                                "Native Lib",
                                List.of(),
                                nativeJar,
                                null,
                                null)));
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream file = Files.newOutputStream(path);
                JarOutputStream jar = new JarOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return path;
    }
}
