#version 460 core

in vec2 vUV;
in vec3 vCameraOffset;
in float vShade;
uniform sampler2D uSkin;
uniform bool uOuterLayer;
uniform bool uWorldShading;
uniform vec2 uDistanceFogRange;
uniform vec2 uWaterFogRange;
uniform vec3 uSkyColor;
uniform vec3 uWaterColor;
uniform float uUnderwater;
uniform float uOpacity;
out vec4 fragColor;

void main() {
    fragColor = texture(uSkin, vUV);
    if (uOuterLayer && fragColor.a < 0.5) discard;
    fragColor.a = uOpacity;
    if (uWorldShading) {
        fragColor.rgb *= vShade;
        float haze = smoothstep(uDistanceFogRange.x, uDistanceFogRange.y, length(vCameraOffset.xz));
        fragColor.rgb = mix(fragColor.rgb, uSkyColor, haze * (1.0 - uUnderwater));
        float distanceInWater = max(length(vCameraOffset) - uWaterFogRange.x, 0.0);
        float waterHaze = (1.0 - exp(-distanceInWater * uWaterFogRange.y)) * uUnderwater;
        vec3 tinted = fragColor.rgb * mix(vec3(1.0), vec3(.78, .94, 1.0), uUnderwater);
        fragColor.rgb = mix(tinted, uWaterColor, waterHaze);
    }
}
