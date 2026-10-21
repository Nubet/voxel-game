#version 460 core

in vec2 vUv;

uniform sampler2D uFire;
// Vertical frame strip: frame index and frame count select the visible 16x16 frame.
uniform int uFrame;
uniform int uFrames;

out vec4 fragColor;

void main() {
    vec4 texel = texture(uFire, vec2(vUv.x, (vUv.y + float(uFrame)) / float(uFrames)));
    if (texel.a < 0.5) discard;
    fragColor = vec4(texel.rgb, 1.0);
}
