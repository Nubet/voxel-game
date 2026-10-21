#version 460 core

uniform sampler2D uFrame;
// x: brightness offset in [-0.25, 0.25], y: contrast factor, z: gamma.
uniform vec3 uAdjust;

out vec4 fragColor;

void main() {
    vec3 color = texelFetch(uFrame, ivec2(gl_FragCoord.xy), 0).rgb;
    color = pow(color, vec3(1.0 / uAdjust.z));
    color = (color - 0.5) * uAdjust.y + 0.5;
    // Raising brightness lifts the black level toward white so shadows open up without clipping highlights.
    color = uAdjust.x >= 0.0 ? mix(color, vec3(1.0), uAdjust.x) : color * (1.0 + uAdjust.x);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
