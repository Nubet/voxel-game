#version 460 core

in vec2 vUv;

uniform sampler2D uTexture;
// Animated sprites are vertical frame strips; single images use one frame.
uniform int uFrame;
uniform int uFrames;

out vec4 fragColor;

void main() {
    vec4 texel = texture(uTexture, vec2(vUv.x, (vUv.y + float(uFrame)) / float(uFrames)));
    if (texel.a < 0.5) discard;
    fragColor = vec4(texel.rgb, 1.0);
}
