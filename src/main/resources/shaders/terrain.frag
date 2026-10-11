#version 330 core

in vec3 vColor;
in vec2 vUv;
flat in float vLayer;
in float vShade;

uniform int uTextured;
uniform sampler2DArray uBlockTextures;

out vec4 fragColor;

void main() {
    if (uTextured != 0) {
        fragColor = vec4(texture(uBlockTextures, vec3(vUv, vLayer)).rgb * vShade, 1.0);
    } else {
        fragColor = vec4(vColor, 1.0);
    }
}
