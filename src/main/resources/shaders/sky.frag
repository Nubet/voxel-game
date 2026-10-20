#version 460 core

in vec2 vClip;
uniform mat4 uInverseVP;
uniform vec3 uZenith;
uniform vec3 uHorizon;
uniform vec3 uSunDirection;
uniform float uTwilight;
uniform float uStars;
uniform float uUnderwater;
uniform float uWaterLight;
uniform float uOvercast;
uniform vec3 uCloudColor;
uniform float uCelestialVisibility;
uniform sampler2D uSun;
uniform sampler2D uMoon;
out vec4 fragColor;

vec4 celestial(sampler2D image, vec3 ray, vec3 direction, float size) {
    float facing = dot(ray, direction);
    if (facing <= 0.0) return vec4(0.0);
    vec3 right = vec3(0.0, 0.0, 1.0);
    vec3 up = cross(right, direction);
    vec2 uv = vec2(dot(ray, right), -dot(ray, up)) / (facing * size) + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec4(0.0);
    return texture(image, uv);
}

void main() {
    vec4 point = uInverseVP * vec4(vClip, 1.0, 1.0);
    vec3 ray = normalize(point.xyz / point.w);
    vec3 color = mix(uHorizon, uZenith, smoothstep(0.0, 0.8, max(ray.y, 0.0)));
    float horizontal = max(length(ray.xz), 0.0001);
    float glow = pow(max(ray.x * sign(uSunDirection.x) / horizontal, 0.0), 3.0);
    glow *= exp(-abs(ray.y) * 8.0) * uTwilight;
    color = mix(color, vec3(1.0, 0.39, 0.12), glow * 0.55);
    vec2 cloudCell = floor(ray.xz / max(ray.y, 0.12) * 10.0);
    float cloudShade = 0.85 + 0.15 * fract(sin(dot(cloudCell, vec2(12.9898, 78.233))) * 43758.5453);
    color = mix(color, uCloudColor * cloudShade, uOvercast * smoothstep(0.0, 0.45, ray.y) * 0.65);
    vec3 grid = ray * 180.0;
    vec3 cell = floor(grid);
    float seed = fract(sin(dot(cell, vec3(127.1, 311.7, 74.7))) * 43758.5453);
    float star = step(0.996, seed) * (1.0 - smoothstep(0.05, 0.27, length(fract(grid) - 0.5)));
    color += vec3(0.78, 0.85, 1.0) * star * uStars * smoothstep(0.0, 0.15, ray.y);
    vec4 sun = celestial(uSun, ray, uSunDirection, 0.18);
    vec4 moon = celestial(uMoon, ray, -uSunDirection, 0.14);
    float aboveHorizon = smoothstep(-0.025, 0.015, ray.y);
    color = mix(color, sun.rgb, sun.a * aboveHorizon * uCelestialVisibility);
    color = mix(color, moon.rgb, moon.a * aboveHorizon * uCelestialVisibility);
    color = mix(color, vec3(0.035, 0.20, 0.27) * uWaterLight, uUnderwater);
    fragColor = vec4(color, 1.0);
}
