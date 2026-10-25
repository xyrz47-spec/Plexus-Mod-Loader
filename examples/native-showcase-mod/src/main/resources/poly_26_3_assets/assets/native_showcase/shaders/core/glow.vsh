#version 330
#extension GL_ARB_separate_shader_objects : require

// Minecraft 26.3 compiles shaders with shaderc: #include instead of #moj_import, and a location on every
// input and output. The 26.2 form is in the jar's assets/; pack.mcmeta picks this overlay on 26.3.
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
