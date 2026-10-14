#version 330 core

in vec4 vColor;
in vec2 vUv;
uniform sampler2D uAtlas;
out vec4 fragColor;

void main() {
    fragColor = vColor * texture(uAtlas, vUv);
}
