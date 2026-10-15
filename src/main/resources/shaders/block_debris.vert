#version 460 core

layout(location = 0) in vec3 aCenter;
layout(location = 1) in vec2 aUv;
layout(location = 2) in float aLayer;
layout(location = 3) in float aSize;
layout(location = 4) in float aAlpha;
uniform mat4 uVP;
out vec2 vUv;
flat out float vLayer;
flat out float vAlpha;

const vec2 CORNERS[6] = vec2[](vec2(0, 0), vec2(1, 0), vec2(1, 1), vec2(1, 1), vec2(0, 1), vec2(0, 0));

void main() {
    vec2 corner = CORNERS[gl_VertexID];
    vec3 right = normalize(vec3(uVP[0][0], uVP[1][0], uVP[2][0]));
    vec3 up = normalize(vec3(uVP[0][1], uVP[1][1], uVP[2][1]));
    vec3 point = aCenter + (right * (corner.x - .5) + up * (corner.y - .5)) * aSize;
    gl_Position = uVP * vec4(point, 1);
    vUv = aUv + corner * .25;
    vLayer = aLayer;
    vAlpha = aAlpha;
}
