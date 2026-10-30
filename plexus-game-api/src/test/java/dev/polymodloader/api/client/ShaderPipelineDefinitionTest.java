package dev.polymodloader.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.polymodloader.api.resource.ResourceId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderPipelineDefinitionTest {

    private static final ResourceId SHADER = ResourceId.of("my_mod", "core/pulse");
    private static final ShaderUniform.Source ZERO = () -> new float[] {0};

    @Test
    void plainPipelineHasNoTexturesOrUniforms() {
        ShaderPipelineDefinition plain =
                ShaderPipelineDefinition.builder(SHADER).build();
        assertFalse(plain.textured());
        assertEquals(List.of(), plain.uniforms());
        assertEquals(
                plain,
                new ShaderPipelineDefinition(SHADER, ShaderPipelineDefinition.Blend.TRANSLUCENT, false, true, false));
    }

    @Test
    void texturesAndUniformsKeepTheirOrder() {
        ResourceId noise = ResourceId.of("my_mod", "textures/effect/noise.png");
        ResourceId mask = ResourceId.of("my_mod", "dynamic/mask");
        ShaderPipelineDefinition definition = ShaderPipelineDefinition.builder(SHADER)
                .texture(noise)
                .texture(mask)
                .uniform("Strength", ShaderUniform.Type.FLOAT, ZERO)
                .uniform("Tint", ShaderUniform.Type.VEC4, () -> new float[] {1, 1, 1, 1})
                .build();
        assertTrue(definition.textured());
        assertEquals(List.of(noise, mask), definition.textures());
        assertEquals(
                List.of("Strength", "Tint"),
                definition.uniforms().stream().map(ShaderUniform::name).toList());
        assertEquals(4, definition.uniforms().get(1).type().components());
    }

    @Test
    void definitionIsACopy() {
        List<ResourceId> textures = new ArrayList<>(List.of(ResourceId.of("my_mod", "textures/a.png")));
        ShaderPipelineDefinition definition = new ShaderPipelineDefinition(
                SHADER, ShaderPipelineDefinition.Blend.OPAQUE, false, true, true, textures, List.of());
        textures.clear();
        assertEquals(1, definition.textures().size());
    }

    @Test
    void rejectsTooManyTextures() {
        ShaderPipelineDefinition.Builder builder = ShaderPipelineDefinition.builder(SHADER);
        for (int i = 0; i <= ShaderPipelineDefinition.MAX_TEXTURES; i++) {
            builder.texture(ResourceId.of("my_mod", "textures/t" + i + ".png"));
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(error.getMessage().startsWith("PLEXUS-CLIENT-002"), error.getMessage());
    }

    @Test
    void rejectsTooManyOrRepeatedUniforms() {
        ShaderPipelineDefinition.Builder many = ShaderPipelineDefinition.builder(SHADER);
        for (int i = 0; i <= ShaderPipelineDefinition.MAX_UNIFORMS; i++) {
            many.uniform("Value" + i, ShaderUniform.Type.FLOAT, ZERO);
        }
        assertThrows(IllegalArgumentException.class, many::build);

        ShaderPipelineDefinition.Builder repeated = ShaderPipelineDefinition.builder(SHADER)
                .uniform("Strength", ShaderUniform.Type.FLOAT, ZERO)
                .uniform("Strength", ShaderUniform.Type.VEC2, () -> new float[] {0, 0});
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, repeated::build);
        assertTrue(error.getMessage().contains("Strength twice"), error.getMessage());
    }

    @Test
    void uniformNamesMustBeGlslIdentifiers() {
        for (String bad : List.of("", "2fast", "has space", "gl_Position", "dash-name")) {
            assertThrows(
                    IllegalArgumentException.class, () -> new ShaderUniform(bad, ShaderUniform.Type.FLOAT, ZERO), bad);
        }
        assertEquals("_Glow2", new ShaderUniform("_Glow2", ShaderUniform.Type.FLOAT, ZERO).name());
    }

    @Test
    void plainVertexHasTextureCoordinateZero() {
        List<float[]> seen = new ArrayList<>();
        VertexSink sink = new VertexSink() {
            @Override
            public VertexSink vertex(double x, double y, double z, float u, float v, int argb) {
                seen.add(new float[] {u, v});
                return this;
            }
        };
        sink.vertex(1, 2, 3, 0xFFFFFFFF).vertex(1, 2, 3, 0.5f, 1, 0xFFFFFFFF);
        assertEquals(0, seen.get(0)[0]);
        assertEquals(0, seen.get(0)[1]);
        assertEquals(0.5f, seen.get(1)[0]);
    }
}
