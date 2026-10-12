#version 330 core

in vec3 vColor;
in vec2 vUv;
flat in float vLayer;
in float vShade;

uniform int uTextured;
uniform int uRenderLayer;
uniform sampler2DArray uBlockTextures;

out vec4 fragColor;

void main() {
    if (uTextured != 0) {
        vec4 texel = texture(uBlockTextures, vec3(vUv, vLayer));
        if (uRenderLayer == 1 && texel.a < 0.5) discard;
        fragColor = vec4(texel.rgb * vShade, uRenderLayer == 2 ? 0.55 : 1.0);
    } else {
        fragColor = vec4(vColor, 1.0);
    }
}
