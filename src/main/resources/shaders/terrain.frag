#version 460 core

in vec2 vUv;
flat in float vLayer;
in float vShade;
flat in vec2 vFlow;
in vec3 vCameraOffset;

uniform int uRenderLayer;
uniform sampler2DArray uBlockTextures;
uniform float uWaterTime;
uniform vec4 uWaterFog;
uniform vec2 uWaterFogRange;
// Chunk columns that near terrain fully draws, one bit each, row-major over uCoverageSize columns. Far terrain hides
// there. uCoverageOrigin is the first column's corner relative to the camera; near terrain passes size 0.
layout(std430, binding = 2) readonly buffer Coverage {
    uint coverage[];
};
uniform vec2 uCoverageOrigin;
uniform int uCoverageSize;

out vec4 fragColor;

void main() {
    if (uCoverageSize > 0) {
        ivec2 column = ivec2(floor((vCameraOffset.xz - uCoverageOrigin) / 16.0));
        if (all(greaterThanEqual(column, ivec2(0))) && all(lessThan(column, ivec2(uCoverageSize)))) {
            int index = column.y * uCoverageSize + column.x;
            if (((coverage[index >> 5] >> (index & 31)) & 1u) != 0u) discard;
        }
    }
    vec2 uv = vUv - (uRenderLayer == 2 ? vFlow * uWaterTime * 0.25 : vec2(0.0));
    vec4 texel = texture(uBlockTextures, vec3(uv, vLayer));
    if (uRenderLayer == 1 && texel.a < 0.5) discard;
    fragColor = vec4(texel.rgb * vShade, uRenderLayer == 2 ? 0.55 : 1.0);
    if (uWaterFog.a > 0.0) {
        float distanceInWater = max(length(vCameraOffset) - uWaterFogRange.x, 0.0);
        float haze = (1.0 - exp(-distanceInWater * uWaterFogRange.y)) * uWaterFog.a;
        vec3 tinted = fragColor.rgb * mix(vec3(1.0), vec3(0.78, 0.94, 1.0), uWaterFog.a);
        fragColor.rgb = mix(tinted, uWaterFog.rgb, haze);
    }
}
