#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// A slow pulse: GameTime grows by 1/24000 per tick, so this repeats about every 3 seconds.
void main() {
    float pulse = 0.6 + 0.4 * sin(GameTime * 2400.0);
    fragColor = vec4(vertexColor.rgb * pulse, vertexColor.a) * ColorModulator;
}
