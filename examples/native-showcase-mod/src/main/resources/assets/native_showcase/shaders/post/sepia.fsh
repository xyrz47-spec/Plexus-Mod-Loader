#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SepiaConfig {
    float Amount;
};

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    vec3 sepia = vec3(
        dot(color.rgb, vec3(0.393, 0.769, 0.189)),
        dot(color.rgb, vec3(0.349, 0.686, 0.168)),
        dot(color.rgb, vec3(0.272, 0.534, 0.131)));
    fragColor = vec4(mix(color.rgb, min(sepia, vec3(1.0)), Amount), 1.0);
}
