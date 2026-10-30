package dev.plexusmodloader.api.client;

import dev.plexusmodloader.api.resource.ResourceId;
import dev.plexusmodloader.api.stability.ApiStability;
import dev.plexusmodloader.api.stability.StabilityLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A render pipeline with a mod's own core shader: GLSL files {@code assets/<namespace>/shaders/<path>.vsh}
 * and {@code .fsh} in the mod's resources, for quads with a position and a colour per vertex, and a
 * texture coordinate when the pipeline has textures.
 *
 * <p>The vertex shader receives {@code in vec3 Position} and {@code in vec4 Color}, plus {@code in
 * vec2 UV0} when the pipeline has textures. Use the game's uniform blocks for the transforms and time,
 * as the game's {@code core/position_color} shader does: {@code #moj_import
 * <minecraft:dynamictransforms.glsl>} and {@code <minecraft:projection.glsl>} for {@code ModelViewMat},
 * {@code ProjMat} and {@code ColorModulator}, and {@code <minecraft:globals.glsl>} for {@code GameTime}.
 *
 * <p>Textures are bound in order as {@code uniform sampler2D Sampler0}, {@code Sampler1}, and so on.
 * Each is a texture file such as {@code my_mod:textures/effect/noise.png} or a {@link DynamicTexture};
 * a missing file shows the game's missing-texture pattern. The mod's own values are the uniform block
 * {@code ModUniforms} (see {@link ShaderUniform}).
 *
 * <p>A shader that fails to compile is reported in the log ({@code PLEXUS-CLIENT-006}), and its quads are
 * skipped.
 *
 * @param shader the shader files' location without extension, for example {@code my_mod:core/glow}
 * @param blend how the quads mix with what is behind them
 * @param cull whether quads facing away from the camera are skipped
 * @param depthTest whether blocks in front hide the quads
 * @param depthWrite whether the quads hide what is drawn behind them later
 * @param textures the textures bound as {@code Sampler0} onwards, at most {@link #MAX_TEXTURES}
 * @param uniforms the fields of {@code ModUniforms} in order, at most {@link #MAX_UNIFORMS}, with
 *     unique names
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ShaderPipelineDefinition(
        ResourceId shader,
        Blend blend,
        boolean cull,
        boolean depthTest,
        boolean depthWrite,
        List<ResourceId> textures,
        List<ShaderUniform> uniforms) {

    /** The most textures one pipeline binds. */
    public static final int MAX_TEXTURES = 4;

    /** The most fields in one pipeline's {@code ModUniforms} block. */
    public static final int MAX_UNIFORMS = 16;

    /**
     * Validates the definition.
     *
     * @throws IllegalArgumentException with {@code PLEXUS-CLIENT-002} for too many textures or uniforms,
     *     or two uniforms with the same name
     */
    public ShaderPipelineDefinition {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(blend, "blend");
        textures = List.copyOf(textures);
        uniforms = List.copyOf(uniforms);
        if (textures.size() > MAX_TEXTURES) {
            throw new IllegalArgumentException("PLEXUS-CLIENT-002 shader pipeline " + shader + " has " + textures.size()
                    + " textures; at most " + MAX_TEXTURES + " are allowed");
        }
        if (uniforms.size() > MAX_UNIFORMS) {
            throw new IllegalArgumentException("PLEXUS-CLIENT-002 shader pipeline " + shader + " has " + uniforms.size()
                    + " uniforms; at most " + MAX_UNIFORMS + " are allowed");
        }
        Set<String> names = new HashSet<>();
        for (ShaderUniform uniform : uniforms) {
            if (!names.add(uniform.name())) {
                throw new IllegalArgumentException("PLEXUS-CLIENT-002 shader pipeline " + shader + " declares uniform "
                        + uniform.name() + " twice");
            }
        }
    }

    /** A pipeline without textures or mod uniforms. */
    public ShaderPipelineDefinition(
            ResourceId shader, Blend blend, boolean cull, boolean depthTest, boolean depthWrite) {
        this(shader, blend, cull, depthTest, depthWrite, List.of(), List.of());
    }

    /** Whether the vertices carry texture coordinates ({@code UV0}). */
    public boolean textured() {
        return !textures.isEmpty();
    }

    /** How a pipeline's colours mix with the scene. */
    public enum Blend {
        /** Replaces what is behind; alpha is ignored. */
        OPAQUE,
        /** Mixes by alpha, like stained glass. */
        TRANSLUCENT,
        /** Adds light, for glows. */
        ADDITIVE
    }

    /** A translucent, double-sided pipeline that blocks hide and that hides nothing. */
    public static Builder builder(ResourceId shader) {
        return new Builder(shader);
    }

    /** Builds a {@link ShaderPipelineDefinition}. */
    public static final class Builder {

        private final ResourceId shader;
        private Blend blend = Blend.TRANSLUCENT;
        private boolean cull;
        private boolean depthTest = true;
        private boolean depthWrite;
        private final List<ResourceId> textures = new ArrayList<>();
        private final List<ShaderUniform> uniforms = new ArrayList<>();

        private Builder(ResourceId shader) {
            this.shader = shader;
        }

        /** How colours mix; {@link Blend#TRANSLUCENT} by default. */
        public Builder blend(Blend blend) {
            this.blend = blend;
            return this;
        }

        /** Skips quads facing away from the camera. */
        public Builder cull() {
            this.cull = true;
            return this;
        }

        /** Draws the quads in front of blocks. */
        public Builder noDepthTest() {
            this.depthTest = false;
            return this;
        }

        /** The quads hide what is drawn behind them later, like solid geometry. */
        public Builder depthWrite() {
            this.depthWrite = true;
            return this;
        }

        /**
         * Binds a texture as the next sampler: the first call is {@code Sampler0}. Its vertices then
         * need texture coordinates ({@link VertexSink#vertex(double, double, double, float, float, int)}).
         *
         * @param texture a texture file, for example {@code my_mod:textures/effect/noise.png}, or a
         *     {@link DynamicTexture#id()}
         */
        public Builder texture(ResourceId texture) {
            textures.add(Objects.requireNonNull(texture, "texture"));
            return this;
        }

        /**
         * Adds a field to the pipeline's {@code ModUniforms} block, after the ones added before.
         *
         * @throws IllegalArgumentException for an invalid name ({@code PLEXUS-CLIENT-002})
         */
        public Builder uniform(String name, ShaderUniform.Type type, ShaderUniform.Source source) {
            uniforms.add(new ShaderUniform(name, type, source));
            return this;
        }

        /**
         * Creates the definition.
         *
         * @throws IllegalArgumentException for too many textures or uniforms, or a repeated uniform name
         *     ({@code PLEXUS-CLIENT-002})
         */
        public ShaderPipelineDefinition build() {
            return new ShaderPipelineDefinition(shader, blend, cull, depthTest, depthWrite, textures, uniforms);
        }
    }
}
