#version 460 core

uniform sampler2DArray uBlockTextures;
uniform sampler2DArray uItemTextures;
uniform bool uItemTexture;
uniform float uDaylight = 1.0;
uniform float uBlockLight = 0.0;
in vec2 vUv;
flat in int vLayer;
flat in float vLight;
out vec4 fragColor;

void main() {
    vec4 color;
    if (uItemTexture) {
        color = texture(uItemTextures, vec3(vUv, vLayer));
    } else {
        color = texture(uBlockTextures, vec3(vUv, vLayer));
        if (vLayer == 1) color.rgb *= vec3(.32, .69, .22);
        if (vLayer == 11 || vLayer == 45 || vLayer == 48 || (vLayer >= 56 && vLayer <= 59))
            color.rgb *= vec3(.3, .55, .22);
        if (vLayer == 3) {
            vec4 overlay = texture(uBlockTextures, vec3(vUv, 49));
            color.rgb = mix(color.rgb, overlay.rgb * vec3(.32, .69, .22), overlay.a);
        }
    }
    if (color.a < .1) discard;
    // Same torch curve as terrain.frag.
    float fraction = uBlockLight / 15.0;
    float curve = fraction / (4.0 - 3.0 * fraction);
    float inverse = 1.0 - curve;
    vec3 torch = vec3(1.0, 0.86, 0.66) * mix(curve, 1.0 - inverse * inverse * inverse * inverse, 0.5);
    fragColor = vec4(color.rgb * vLight * max(vec3(uDaylight), torch), color.a);
}
