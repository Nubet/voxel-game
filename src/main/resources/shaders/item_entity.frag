#version 460 core

uniform sampler2DArray uBlockTextures;
in vec2 vUv;
flat in int vLayer;
flat in float vLight;
out vec4 fragColor;

void main() {
    vec4 color = texture(uBlockTextures, vec3(vUv, vLayer));
    if (color.a < .1) discard;
    fragColor = vec4(color.rgb * vLight, color.a);
}
