package dev.plexusmodloader.game.layer.v26_3.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.HintsAndWorkarounds;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.util.ShaderCompileException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The example native-showcase-mod's shaders against the GLSL compiler of Minecraft 26.3 (shaderc to SPIR-V), with
 * the game's own include files from the client jar. 26.3 needs {@code #include} and a location on every input and
 * output, so the mod keeps a 26.3 form in the overlay {@code plexus_26_3_assets} that its {@code pack.mcmeta} picks
 * on 26.3; its 26.2 form in {@code assets/} does not compile here. The public source has no examples/, so the tests are
 * skipped there.
 */
class ModShaderSourcesTest {

    private static final Path RESOURCES = Path.of(System.getProperty("plexus.exampleResources"));
    private static final String SHADERS = "assets/native_showcase/shaders/";

    private static GlslCompiler compiler;
    private static Field device;

    /**
     * The compiler reads the device's hints and workarounds when it sets its options; without a window there is no
     * device, so the game's slot holds one that answers only that, as a desktop OpenGL device without workarounds.
     */
    @BeforeAll
    static void startCompiler() throws ReflectiveOperationException {
        assumeTrue(Files.isDirectory(RESOURCES), "examples/ is not in this source");
        DeviceInfo info = new DeviceInfo(
                "test",
                "test",
                "test",
                false,
                "OpenGL",
                1.0f,
                null,
                null,
                Set.of(),
                new HintsAndWorkarounds(false, false, false, false),
                null);
        GpuDevice hintsOnly = (GpuDevice) Proxy.newProxyInstance(
                GpuDevice.class.getClassLoader(), new Class<?>[] {GpuDevice.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getDeviceInfo")) {
                        return info;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        device = RenderSystem.class.getDeclaredField("DEVICE");
        device.setAccessible(true);
        device.set(null, hintsOnly);
        compiler = new GlslCompiler(false, false);
    }

    @AfterAll
    static void closeCompiler() throws IllegalAccessException {
        if (compiler == null) {
            return;
        }
        compiler.close();
        device.set(null, null);
    }

    @ParameterizedTest
    @CsvSource({
        "core/glow.vsh, VERTEX",
        "core/glow.fsh, FRAGMENT",
        "core/pulse.vsh, VERTEX",
        "core/pulse.fsh, FRAGMENT",
        "post/sepia.fsh, FRAGMENT"
    })
    void overlayShadersCompile(String file, ShaderType type) throws ShaderCompileException {
        try (SpvModule module = compile(RESOURCES.resolve("plexus_26_3_assets").resolve(SHADERS + file), type)) {
            assertTrue(module.spv().remaining() > 0, file);
        }
    }

    @Test
    void the26_2FormDoesNotCompile() {
        assertThrows(
                ShaderCompileException.class,
                () -> compile(RESOURCES.resolve(SHADERS + "core/pulse.vsh"), ShaderType.VERTEX)
                        .close());
    }

    private static SpvModule compile(Path file, ShaderType type) throws ShaderCompileException {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (ShaderSource includes = new GameIncludes()) {
            return compiler.compileToSpv(file.getFileName().toString(), source, type, ShaderDefines.EMPTY, includes);
        }
    }

    /** The game's include files, {@code <namespace:name.glsl>} read from {@code assets/<namespace>/shaders/include/}. */
    private static final class GameIncludes implements ShaderSource {

        @Override
        public String getShader(Identifier id, ShaderType type) {
            throw new UnsupportedOperationException("only includes are read: " + id);
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            String path = "assets/" + id.getNamespace() + "/shaders/include/" + id.getPath();
            try (InputStream in = ModShaderSourcesTest.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    return CachedIncludeSource.createError("not found: " + path);
                }
                return CachedIncludeSource.create(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() {}
    }
}
