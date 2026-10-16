#version 460 core

in vec2 vOffset;

uniform sampler2D uClouds;
uniform vec2 uPhase;
uniform float uPeriod;
uniform float uRadius;
uniform float uVisibility;

out vec4 fragColor;

void main() {
    float mask = texture(uClouds, (vOffset + uPhase) / uPeriod).r;
    float fade = 1.0 - smoothstep(uRadius * 0.65, uRadius, length(vOffset));
    if (mask < 0.5 || fade <= 0.0) discard;
    fragColor = vec4(vec3(1.0), 0.8 * fade * uVisibility);
}
