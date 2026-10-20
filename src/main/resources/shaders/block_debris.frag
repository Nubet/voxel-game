#version 460 core

in vec2 vUv;
flat in float vLayer;
flat in float vAlpha;
uniform sampler2DArray uBlockTextures;
uniform float uDaylight = 1.0;
out vec4 fragColor;

void main() {
    vec4 texel = texture(uBlockTextures, vec3(vUv, vLayer));
    if (texel.a < .5) discard;
    fragColor = vec4(texel.rgb * .85 * uDaylight, texel.a * vAlpha);
}
