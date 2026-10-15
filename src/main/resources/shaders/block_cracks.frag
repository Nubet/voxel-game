#version 460 core

in vec2 vUv;
flat in int vFace;
uniform float uProgress;
out vec4 fragColor;

vec2 hash(vec2 cell) {
    return fract(sin(vec2(dot(cell, vec2(127.1, 311.7)), dot(cell, vec2(269.5, 183.3)))) * 43758.5453);
}

void main() {
    vec2 pixel = (floor(vUv * 32.0) + .5) / 32.0 * 4.0;
    vec2 cell = floor(pixel);
    float nearest = 100.0, second = 100.0;
    vec2 seed = vec2(0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 id = cell + vec2(x, y);
            vec2 site = id + .2 + .6 * hash(id + float(vFace) * 13.0);
            float distance = length(pixel - site);
            if (distance < nearest) {
                second = nearest;
                nearest = distance;
                seed = id;
            } else second = min(second, distance);
        }
    }
    float reveal = all(equal(seed, vec2(1))) ? 0.0 : hash(seed + float(vFace) * 13.0).x;
    if (second - nearest > mix(.04, .16, uProgress) || reveal > uProgress) discard;
    fragColor = vec4(.06, .05, .04, .85);
}
