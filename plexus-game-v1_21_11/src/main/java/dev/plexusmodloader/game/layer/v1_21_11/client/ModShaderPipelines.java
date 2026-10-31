package dev.plexusmodloader.game.layer.v1_21_11.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.plexusmodloader.api.client.ShaderPipeline;
import dev.plexusmodloader.api.client.ShaderPipelineDefinition;
import dev.plexusmodloader.api.client.ShaderUniform;
import dev.plexusmodloader.api.client.VertexSink;
import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.game.layer.v1_21_11.LayerState;
import dev.plexusmodloader.game.layer.v1_21_11.Views;
import dev.plexusmodloader.game.version.layer.NativeDeclarations.ShaderPipelineDeclaration;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render pipelines with mods' core shaders, built into the game's {@link RenderPipeline} and {@link
 * RenderType} on first use on the render thread. A pipeline whose shaders do not compile is reported
 * once ({@code PLEXUS-CLIENT-006}) and its quads are skipped.
 *
 * <p>Textures are bound through the render type as {@code Sampler0} onwards. Mod uniforms live in one
 * uniform buffer per pipeline: {@link #uploadUniforms()} writes this frame's values before the world is
 * drawn, and {@link #bindUniforms} binds the buffer as {@code ModUniforms} whenever a render pass
 * switches to that pipeline.
 */
final class ModShaderPipelines {

    private static final Logger LOGGER = LoggerFactory.getLogger("Plexus Mod Loader");

    /** The uniform block name mod shaders declare. */
    static final String UNIFORM_BLOCK = "ModUniforms";

    /** The built pipeline per ID, or empty when it failed. */
    private static final Map<ResourceId, Optional<Built>> BUILT = new ConcurrentHashMap<>();

    /** Uniform buffers by game pipeline, read on every render pass pipeline switch. */
    private static final Map<RenderPipeline, UniformBlock> UNIFORMS_BY_PIPELINE = new ConcurrentHashMap<>();

    private static final List<UniformBlock> UNIFORM_BLOCKS = new CopyOnWriteArrayList<>();

    private ModShaderPipelines() {}

    /**
     * One quad batch: absolute or block-local vertices, already relative to the pose's origin, with a
     * texture coordinate pair per vertex.
     */
    record Quads(ShaderPipeline pipeline, float[] positions, float[] uvs, int[] colors) {}

    private record Built(RenderType type, boolean textured) {}

    /** A pipeline's uniform buffer and the last good values of its fields. */
    private static final class UniformBlock {

        final ResourceId id;
        final String ownerModId;
        final List<ShaderUniform> uniforms;
        final GpuBuffer buffer;
        final float[][] values;
        volatile boolean failed;
        volatile boolean uploadReported;
        volatile boolean bindReported;
        final boolean[] valueReported;

        UniformBlock(ResourceId id, String ownerModId, List<ShaderUniform> uniforms, GpuBuffer buffer) {
            this.id = id;
            this.ownerModId = ownerModId;
            this.uniforms = uniforms;
            this.buffer = buffer;
            this.values = new float[uniforms.size()][];
            this.valueReported = new boolean[uniforms.size()];
            for (int i = 0; i < uniforms.size(); i++) {
                values[i] = new float[uniforms.get(i).type().components()];
            }
        }
    }

    /** Records the vertices a mod writes, shifted by {@code -originX/Y/Z}. */
    static Quads record(
            ShaderPipeline pipeline, Consumer<VertexSink> vertices, double originX, double originY, double originZ) {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        VertexSink sink = new VertexSink() {
            @Override
            public VertexSink vertex(double x, double y, double z, float u, float v, int argb) {
                positions.add(new float[] {(float) (x - originX), (float) (y - originY), (float) (z - originZ)});
                uvs.add(new float[] {u, v});
                colors.add(argb);
                return this;
            }
        };
        vertices.accept(sink);
        int complete = positions.size() - positions.size() % 4;
        float[] flatPositions = new float[complete * 3];
        float[] flatUvs = new float[complete * 2];
        int[] argb = new int[complete];
        for (int i = 0; i < complete; i++) {
            System.arraycopy(positions.get(i), 0, flatPositions, i * 3, 3);
            System.arraycopy(uvs.get(i), 0, flatUvs, i * 2, 2);
            argb[i] = colors.get(i);
        }
        return new Quads(pipeline, flatPositions, flatUvs, argb);
    }

    /** Submits {@code quads} with {@code poseStack}; skipped when the pipeline is unknown or broken. */
    static void submit(Quads quads, PoseStack poseStack, SubmitNodeCollector collector) {
        if (quads.colors().length == 0) {
            return;
        }
        built(quads.pipeline().id())
                .ifPresent(built -> collector.submitCustomGeometry(poseStack, built.type(), (pose, buffer) -> {
                    float[] positions = quads.positions();
                    float[] uvs = quads.uvs();
                    int[] colors = quads.colors();
                    for (int i = 0; i < colors.length; i++) {
                        VertexConsumer vertex =
                                buffer.addVertex(pose, positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
                        if (built.textured()) {
                            vertex.setUv(uvs[i * 2], uvs[i * 2 + 1]);
                        }
                        vertex.setColor(colors[i]);
                    }
                }));
    }

    /**
     * Writes this frame's mod uniform values into each pipeline's buffer. Called on the render thread
     * before the world's features are submitted, outside any render pass.
     */
    static void uploadUniforms() {
        for (UniformBlock block : UNIFORM_BLOCKS) {
            if (block.failed) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder data = Std140Builder.onStack(stack, (int) block.buffer.size());
                for (int i = 0; i < block.uniforms.size(); i++) {
                    float[] value = currentValue(block, i);
                    switch (block.uniforms.get(i).type()) {
                        case FLOAT -> data.putFloat(value[0]);
                        case VEC2 -> data.putVec2(value[0], value[1]);
                        case VEC3 -> data.putVec3(value[0], value[1], value[2]);
                        case VEC4 -> data.putVec4(value[0], value[1], value[2], value[3]);
                    }
                }
                ByteBuffer bytes = data.get();
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(block.buffer.slice(), bytes);
                if (!block.uploadReported) {
                    block.uploadReported = true;
                    LOGGER.info("[Plexus Mod Loader] Uploaded {} of mod shader pipeline {}", UNIFORM_BLOCK, block.id);
                }
            } catch (RuntimeException e) {
                block.failed = true;
                LOGGER.error(
                        "[Plexus Mod Loader] PLEXUS-CLIENT-007 cannot upload {} of shader pipeline {} of {}; its values stay as they were",
                        UNIFORM_BLOCK,
                        block.id,
                        block.ownerModId,
                        e);
            }
        }
    }

    /**
     * Binds a mod pipeline's uniform buffer when a render pass switches to it. Runs for every pipeline
     * switch in the game, so it returns at once when no mod pipeline has uniforms.
     */
    static void bindUniforms(RenderPass pass, RenderPipeline pipeline) {
        if (UNIFORMS_BY_PIPELINE.isEmpty()) {
            return;
        }
        UniformBlock block = UNIFORMS_BY_PIPELINE.get(pipeline);
        if (block == null) {
            return;
        }
        pass.setUniform(UNIFORM_BLOCK, block.buffer.slice());
        if (!block.bindReported) {
            block.bindReported = true;
            LOGGER.info(
                    "[Plexus Mod Loader] Bound {} of mod shader pipeline {} ({} field(s), {} bytes)",
                    UNIFORM_BLOCK,
                    block.id,
                    block.uniforms.size(),
                    block.buffer.size());
        }
    }

    /** The value of field {@code index} this frame, or its last good value after a bad one. */
    private static float[] currentValue(UniformBlock block, int index) {
        ShaderUniform uniform = block.uniforms.get(index);
        float[] value;
        try {
            value = uniform.source().values();
        } catch (RuntimeException e) {
            reportValue(block, index, "threw " + e);
            return block.values[index];
        }
        if (value == null || value.length != uniform.type().components()) {
            reportValue(
                    block,
                    index,
                    "returned " + (value == null ? "null" : value.length + " value(s)") + " for a " + uniform.type()
                            + " (" + uniform.type().components() + " expected)");
            return block.values[index];
        }
        System.arraycopy(value, 0, block.values[index], 0, value.length);
        return value;
    }

    private static void reportValue(UniformBlock block, int index, String problem) {
        if (!block.valueReported[index]) {
            block.valueReported[index] = true;
            LOGGER.error(
                    "[Plexus Mod Loader] PLEXUS-CLIENT-007 uniform {} of shader pipeline {} of {} {}; its last good value is used",
                    block.uniforms.get(index).name(),
                    block.id,
                    block.ownerModId,
                    problem);
        }
    }

    private static Optional<Built> built(ResourceId id) {
        return BUILT.computeIfAbsent(id, ModShaderPipelines::create);
    }

    private static Optional<Built> create(ResourceId id) {
        ShaderPipelineDeclaration declaration = LayerState.context()
                .flatMap(context -> context.declarations().shaderPipelines().stream()
                        .filter(candidate -> candidate.id().equals(id))
                        .findFirst())
                .orElse(null);
        if (declaration == null) {
            LOGGER.error("[Plexus Mod Loader] PLEXUS-CLIENT-006 no shader pipeline {} is declared", id);
            return Optional.empty();
        }
        ShaderPipelineDefinition definition = declaration.definition();
        try {
            // 26.x's DepthStencilState.DEFAULT tests LESS_THAN_OR_EQUAL, which is 1.21.11's LEQUAL_DEPTH_TEST.
            DepthTestFunction depth =
                    definition.depthTest() ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST;
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Views.id(ResourceId.of(id.namespace(), "pipeline/" + id.path())))
                    // 1.21.11 has no bind group layouts: the uniforms of 26.2's BindGroupLayouts.GLOBALS and
                    // MATRICES_PROJECTION are declared one by one, as 1.21.11's RenderPipelines does (javap).
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withVertexShader(Views.id(definition.shader()))
                    .withFragmentShader(Views.id(definition.shader()))
                    .withVertexFormat(
                            definition.textured()
                                    ? DefaultVertexFormat.POSITION_TEX_COLOR
                                    : DefaultVertexFormat.POSITION_COLOR,
                            VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(depth)
                    .withDepthWrite(definition.depthWrite())
                    .withCull(definition.cull());
            if (definition.textured()) {
                for (int i = 0; i < definition.textures().size(); i++) {
                    builder.withSampler(samplerName(i));
                }
            }
            if (!definition.uniforms().isEmpty()) {
                builder.withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER);
            }
            switch (definition.blend()) {
                case OPAQUE -> builder.withoutBlend();
                case TRANSLUCENT -> builder.withBlend(BlendFunction.TRANSLUCENT);
                case ADDITIVE -> builder.withBlend(BlendFunction.ADDITIVE);
            }
            RenderPipeline pipeline = builder.build();
            if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
                LOGGER.error(
                        "[Plexus Mod Loader] PLEXUS-CLIENT-006 shader pipeline {} of {} did not compile ({}.vsh/.fsh); its quads are skipped",
                        id,
                        declaration.ownerModId(),
                        definition.shader());
                return Optional.empty();
            }
            RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline);
            for (int i = 0; i < definition.textures().size(); i++) {
                setup.withTexture(samplerName(i), Views.id(definition.textures().get(i)));
            }
            if (definition.blend() != ShaderPipelineDefinition.Blend.OPAQUE) {
                setup.sortOnUpload();
            }
            Method create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            create.setAccessible(true);
            RenderType type = (RenderType) create.invoke(null, id.toString(), setup.createRenderSetup());
            if (!definition.uniforms().isEmpty()) {
                UniformBlock block = new UniformBlock(
                        id, declaration.ownerModId(), definition.uniforms(), createUniformBuffer(id, definition));
                UNIFORM_BLOCKS.add(block);
                UNIFORMS_BY_PIPELINE.put(pipeline, block);
            }
            if (definition.textured() || !definition.uniforms().isEmpty()) {
                LOGGER.info(
                        "[Plexus Mod Loader] Built mod shader pipeline {} ({}) with {} texture(s) and {} uniform(s)",
                        id,
                        definition.shader(),
                        definition.textures().size(),
                        definition.uniforms().size());
            } else {
                LOGGER.info("[Plexus Mod Loader] Built mod shader pipeline {} ({})", id, definition.shader());
            }
            return Optional.of(new Built(type, definition.textured()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error(
                    "[Plexus Mod Loader] PLEXUS-CLIENT-006 cannot build shader pipeline {} of {}",
                    id,
                    declaration.ownerModId(),
                    e);
            return Optional.empty();
        }
    }

    /** A uniform buffer sized for the std140 layout of the pipeline's fields, filled with zeros. */
    private static GpuBuffer createUniformBuffer(ResourceId id, ShaderPipelineDefinition definition) {
        Std140SizeCalculator size = new Std140SizeCalculator();
        for (ShaderUniform uniform : definition.uniforms()) {
            switch (uniform.type()) {
                case FLOAT -> size.putFloat();
                case VEC2 -> size.putVec2();
                case VEC3 -> size.putVec3();
                case VEC4 -> size.putVec4();
            }
        }
        int bytes = size.align(16).get();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return RenderSystem.getDevice()
                    .createBuffer(
                            () -> "Plexus Mod Loader " + UNIFORM_BLOCK + " " + id,
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                            stack.calloc(bytes));
        }
    }

    private static String samplerName(int index) {
        return "Sampler" + index;
    }
}
