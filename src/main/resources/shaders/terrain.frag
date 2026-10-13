#version 330 core

in vec3 vColor;
in vec2 vUv;
flat in float vLayer;
in float vShade;
flat in vec2 vFlow;

uniform int uTextured;
uniform int uRenderLayer;
uniform sampler2DArray uBlockTextures;
uniform float uWaterTime;

out vec4 fragColor;

void main() {
    if (uTextured != 0) {
        vec2 uv = vUv - (uRenderLayer == 2 ? vFlow * uWaterTime * 0.25 : vec2(0.0));
        vec4 texel = texture(uBlockTextures, vec3(uv, vLayer));
        if (uRenderLayer == 1 && texel.a < 0.5) discard;
        fragColor = vec4(texel.rgb * vShade, uRenderLayer == 2 ? 0.55 : 1.0);
    } else {
        fragColor = vec4(vColor, 1.0);
    }
}
