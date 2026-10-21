#version 460 core

uniform sampler2DArray uBlockTextures;
uniform sampler2DArray uItemTextures;
uniform bool uSprite;
uniform float uDaylight = 1.0;
in vec2 vUv;
flat in int vLayer;
flat in float vLight;
out vec4 fragColor;

void main() {
    if (uSprite) {
        vec4 item = texture(uItemTextures, vec3(vUv, vLayer));
        if (item.a < .1) discard;
        fragColor = vec4(item.rgb * uDaylight, item.a);
        return;
    }
    vec4 color = texture(uBlockTextures, vec3(vUv, vLayer));
    if (vLayer == 1) color.rgb *= vec3(.32, .69, .22);
    if (vLayer == 11 || vLayer == 45 || vLayer == 48 || (vLayer >= 56 && vLayer <= 59)) color.rgb *= vec3(.3, .55, .22);
    if (vLayer == 3) {
        vec4 overlay = texture(uBlockTextures, vec3(vUv, 49));
        color.rgb = mix(color.rgb, overlay.rgb * vec3(.32, .69, .22), overlay.a);
    }
    if (color.a < .1) discard;
    fragColor = vec4(color.rgb * vLight * uDaylight, color.a);
}
