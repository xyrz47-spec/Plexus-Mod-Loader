#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

// The mod's own values, in the order NativeShowcaseClient declares them.
layout(std140) uniform ModUniforms {
    float Strength;
    vec4 Tint;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * Tint;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = vec4(color.rgb * Strength, color.a) * ColorModulator;
}
