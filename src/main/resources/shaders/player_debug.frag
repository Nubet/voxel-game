#version 460 core

in vec2 vUV;
uniform sampler2D uSkin;
out vec4 fragColor;

void main() {
    fragColor = texture(uSkin, vUV);
    if (fragColor.a < 0.5) discard;
}
