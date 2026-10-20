#version 460 core

in vec2 vUv;
flat in float vLayer;
flat in float vTint;
in float vShade;
flat in vec2 vFlow;
in vec3 vCameraOffset;

uniform int uRenderLayer;
uniform sampler2DArray uBlockTextures;
uniform float uWaterTime;
uniform vec4 uWaterFog;
uniform vec2 uWaterFogRange;
uniform vec2 uDistanceFogRange;
uniform vec3 uDistanceFogColor;
uniform float uDaylight = 1.0;
// Chunk columns that near terrain fully draws, one bit each, row-major over uCoverageSize columns. Far terrain hides
// there. uCoverageOrigin is the first column's corner relative to the camera; near terrain passes size 0.
layout(std430, binding = 2) readonly buffer Coverage {
    uint coverage[];
};
uniform vec2 uCoverageOrigin;
uniform int uCoverageSize;

out vec4 fragColor;

vec3 biomeTint(bool foliage) {
    int index = int(vTint);
    int style = index / 8;
    vec3 base = foliage ? vec3(.3, .55, .22) : vec3(.32, .69, .22);
    vec3 target = base;
    if (style == 0) target = foliage ? vec3(.24, .65, .16) : vec3(.28, .78, .16);
    if (style == 1) target = foliage ? vec3(.55, .5, .18) : vec3(.82, .72, .38);
    if (style == 2) target = foliage ? vec3(.22, .45, .18) : vec3(.38, .62, .32);
    if (style == 3) target = foliage ? vec3(.2, .42, .16) : vec3(.24, .48, .18);
    return mix(base, target, float(index % 8) / 7.0);
}

void main() {
    if (uCoverageSize > 0) {
        ivec2 column = ivec2(floor((vCameraOffset.xz - uCoverageOrigin) / 16.0));
        if (all(greaterThanEqual(column, ivec2(0))) && all(lessThan(column, ivec2(uCoverageSize)))) {
            int index = column.y * uCoverageSize + column.x;
            if (((coverage[index >> 5] >> (index & 31)) & 1u) != 0u) discard;
        }
    }
    vec2 uv = vUv - (vTint < 0.0 || uRenderLayer == 2 ? vFlow * uWaterTime * 0.25 : vec2(0.0));
    vec4 texel = texture(uBlockTextures, vec3(uv, vLayer));
    bool foliage = vLayer == 11.0 || vLayer == 45.0 || vLayer == 48.0 || (vLayer >= 56.0 && vLayer <= 59.0);
    if (vLayer == 1.0 || foliage) texel.rgb *= biomeTint(foliage);
    if (vLayer == 3.0) {
        vec4 overlay = texture(uBlockTextures, vec3(uv, 49.0));
        texel.rgb = mix(texel.rgb, overlay.rgb * biomeTint(false), overlay.a);
    }
    if (uRenderLayer != 2 && texel.a < 0.5) discard;
    fragColor = vec4(texel.rgb * vShade * uDaylight, uRenderLayer == 2 ? 0.55 : 1.0);
    if (uDistanceFogRange.y > 0.0) {
        float haze = smoothstep(uDistanceFogRange.x, uDistanceFogRange.y, length(vCameraOffset.xz));
        haze *= 1.0 - uWaterFog.a;
        fragColor.rgb = mix(fragColor.rgb, uDistanceFogColor, haze);
        fragColor.a = mix(fragColor.a, 1.0, haze);
    }
    if (uWaterFog.a > 0.0) {
        float distanceInWater = max(length(vCameraOffset) - uWaterFogRange.x, 0.0);
        float haze = (1.0 - exp(-distanceInWater * uWaterFogRange.y)) * uWaterFog.a;
        vec3 tinted = fragColor.rgb * mix(vec3(1.0), vec3(0.78, 0.94, 1.0), uWaterFog.a);
        fragColor.rgb = mix(tinted, uWaterFog.rgb, haze);
    }
}
