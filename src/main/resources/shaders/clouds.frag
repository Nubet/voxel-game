#version 460 core

in vec2 vOffset;
in float vShade;

uniform sampler2D uClouds;
uniform vec2 uPhase;
uniform float uPeriod;
uniform float uRadius;
uniform float uVisibility;
uniform bool uFancy;

out vec4 fragColor;

void main() {
    float mask = uFancy ? 1.0 : texture(uClouds, (vOffset + uPhase) / uPeriod).r;
    float fade = 1.0 - smoothstep(uRadius * 0.65, uRadius, length(vOffset));
    if (mask < 0.5 || fade <= 0.0) discard;
    float alpha = (uFancy ? 1.0 : 0.8) * fade * uVisibility;
    if (uFancy) {
        const int BAYER[16] = int[](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
        ivec2 pixel = ivec2(gl_FragCoord.xy) & ivec2(3);
        if (alpha <= (float(BAYER[pixel.y * 4 + pixel.x]) + 0.5) / 16.0) discard;
        alpha = 1.0;
    }
    fragColor = vec4(vec3(vShade), alpha);
}
