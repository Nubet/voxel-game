#version 460 core

in vec2 vUV;
in vec3 vPosition;
in float vHeight;
in float vSurface;
in float vAmount;
flat in int vSnow;
uniform sampler2D uRain;
uniform sampler2D uSnow;
uniform float uVisibility;
uniform float uLight;
uniform float uRadius;
out vec4 fragColor;

void main() {
    if (vHeight <= vSurface || vAmount <= 0.0) discard;
    vec4 pixel = vSnow == 1 ? texture(uSnow, vUV) : texture(uRain, vUV);
    float fade = 1.0 - smoothstep(uRadius * 0.65, uRadius, length(vPosition.xz));
    fade *= smoothstep(0.5, 1.5, length(vPosition));
    fade *= 1.0 - smoothstep(10.0, 14.0, abs(vPosition.y));
    float alpha = pixel.a * vAmount * fade * uVisibility * (vSnow == 1 ? 0.9 : 0.65);
    if (alpha < 0.005) discard;
    fragColor = vec4(pixel.rgb * max(uLight, 0.18), alpha);
}
