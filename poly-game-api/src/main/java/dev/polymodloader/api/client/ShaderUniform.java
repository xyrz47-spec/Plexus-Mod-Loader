package dev.polymodloader.api.client;

import dev.polymodloader.api.stability.ApiStability;
import dev.polymodloader.api.stability.StabilityLevel;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One value a mod passes to its own shader pipeline, declared with {@link
 * ShaderPipelineDefinition.Builder#uniform}. A pipeline's values form the uniform block {@code
 * ModUniforms}, in declaration order:
 *
 * <pre>{@code
 * layout(std140) uniform ModUniforms {
 *     float Strength;
 *     vec4 Tint;
 * };
 * }</pre>
 *
 * <p>Poly Mod Loader asks {@link Source#values()} once per frame on the render thread, before the world is
 * drawn, and uploads the result. A source that throws or returns the wrong number of values is
 * reported once ({@code POLY-CLIENT-007}); the pipeline then keeps its last good values, which start
 * as zeros.
 *
 * @param name the field name in GLSL: letters, digits and {@code _}, not starting with a digit or
 *     {@code gl_}
 * @param type the GLSL type
 * @param source gives the value each frame
 */
@ApiStability(StabilityLevel.PREVIEW)
public record ShaderUniform(String name, Type type, Source source) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    public ShaderUniform {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        if (!NAME.matcher(name).matches() || name.startsWith("gl_")) {
            throw new IllegalArgumentException("POLY-CLIENT-002 invalid uniform name '" + name
                    + "': use letters, digits and _, not starting with a digit or gl_");
        }
    }

    /** The GLSL type of a uniform. */
    public enum Type {
        /** {@code float}. */
        FLOAT(1),
        /** {@code vec2}. */
        VEC2(2),
        /** {@code vec3}. */
        VEC3(3),
        /** {@code vec4}. */
        VEC4(4);

        private final int components;

        Type(int components) {
            this.components = components;
        }

        /** How many floats {@link Source#values()} must return. */
        public int components() {
            return components;
        }
    }

    /** Gives a uniform's value; called once per frame on the render thread. */
    @FunctionalInterface
    public interface Source {

        /** The value: exactly {@link Type#components()} floats. */
        float[] values();
    }
}
