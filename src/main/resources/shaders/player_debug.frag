#version 460 core

in vec2 vUV;
in vec3 vCameraOffset;
in float vShade;
uniform sampler2D uSkin;
uniform bool uOuterLayer;
// Mob textures cut holes into their cuboids, such as the gaps between a skeleton's ribs.
uniform bool uCutout;
uniform bool uWorldShading;
uniform vec2 uDistanceFogRange;
uniform vec2 uWaterFogRange;
uniform vec3 uSkyColor;
uniform vec3 uWaterColor;
uniform float uUnderwater;
uniform float uOpacity;
uniform float uDaylight = 1.0;
uniform float uBlockLight = 0.0;
// Minecraft tints a mob red for the half second after it takes damage.
uniform bool uHurt;
// A swelling creeper blinks white, as in Minecraft.
uniform bool uFlash;
out vec4 fragColor;

void main() {
    fragColor = texture(uSkin, vUV);
    if ((uOuterLayer || uCutout) && fragColor.a < 0.5) discard;
    fragColor.a = uOpacity;
    if (uHurt) fragColor.rgb = mix(fragColor.rgb, vec3(1.0, 0.0, 0.0), 0.4);
    if (uFlash) fragColor.rgb = mix(fragColor.rgb, vec3(1.0), 0.5);
    if (uWorldShading) {
        // Same curve and tint as terrain.frag so entities match the ground they stand on.
        float fraction = uBlockLight / 15.0;
        float curve = fraction / (4.0 - 3.0 * fraction);
        float inverse = 1.0 - curve;
        vec3 torch = vec3(1.0, 0.86, 0.66) * mix(curve, 1.0 - inverse * inverse * inverse * inverse, 0.5);
        fragColor.rgb *= vShade * max(vec3(uDaylight), torch);
        float haze = smoothstep(uDistanceFogRange.x, uDistanceFogRange.y, length(vCameraOffset.xz));
        fragColor.rgb = mix(fragColor.rgb, uSkyColor, haze * (1.0 - uUnderwater));
        float distanceInWater = max(length(vCameraOffset) - uWaterFogRange.x, 0.0);
        float waterHaze = (1.0 - exp(-distanceInWater * uWaterFogRange.y)) * uUnderwater;
        vec3 tinted = fragColor.rgb * mix(vec3(1.0), vec3(.78, .94, 1.0), uUnderwater);
        fragColor.rgb = mix(tinted, uWaterColor * uDaylight, waterHaze);
    }
}
