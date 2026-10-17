#version 460 core

in vec2 vUV;
uniform sampler2D uSkin;
uniform bool uOuterLayer;
out vec4 fragColor;

void main() {
    fragColor = texture(uSkin, vUV);
    if (uOuterLayer && fragColor.a < 0.5) discard;
    fragColor.a = 1.0;
}
