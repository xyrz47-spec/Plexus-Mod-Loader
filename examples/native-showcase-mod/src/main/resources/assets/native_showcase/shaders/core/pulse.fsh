#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// The mod's own values, in the order NativeShowcaseClient declares them.
layout(std140) uniform ModUniforms {
    float Strength;
    vec4 Tint;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * Tint;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = vec4(color.rgb * Strength, color.a) * ColorModulator;
}
