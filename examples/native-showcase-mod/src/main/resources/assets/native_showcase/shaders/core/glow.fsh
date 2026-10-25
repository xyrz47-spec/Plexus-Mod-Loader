#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec4 vertexColor;

out vec4 fragColor;

// A slow pulse: GameTime grows by 1/24000 per tick, so this repeats about every 3 seconds.
void main() {
    float pulse = 0.6 + 0.4 * sin(GameTime * 2400.0);
    fragColor = vec4(vertexColor.rgb * pulse, vertexColor.a) * ColorModulator;
}
