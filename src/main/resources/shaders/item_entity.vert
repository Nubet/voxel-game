#version 460 core

uniform mat4 uVP;
uniform vec3 uOffset;
uniform float uAngle;
uniform ivec3 uLayers;
uniform bool uCross;
uniform float uHeight;
out vec2 vUv;
flat out int vLayer;
flat out float vLight;

const vec2 CORNERS[6] = vec2[](vec2(0, 0), vec2(1, 0), vec2(1, 1), vec2(1, 1), vec2(0, 1), vec2(0, 0));

void main() {
    vec2 uv = CORNERS[gl_VertexID % 6];
    int face = gl_VertexID / 6;
    vec3 point;
    if (uCross) point = face == 0 ? vec3(uv.x, uv.y, uv.x) : vec3(uv.x, uv.y, 1 - uv.x);
    else if (face == 0) point = vec3(0, uv.y, uv.x);
    else if (face == 1) point = vec3(1, uv.y, uv.x);
    else if (face == 2) point = vec3(uv.x, 1, uv.y);
    else if (face == 3) point = vec3(uv.x, 0, uv.y);
    else if (face == 4) point = vec3(uv.x, uv.y, 0);
    else point = vec3(uv.x, uv.y, 1);
    point = (point - .5) * .25;
    point.y *= uHeight;
    float c = cos(uAngle), s = sin(uAngle);
    point.xz = mat2(c, s, -s, c) * point.xz;
    vUv = vec2(uv.x, 1 - uv.y);
    vLayer = uCross ? uLayers.x : face == 2 ? uLayers.y : face == 3 ? uLayers.z : uLayers.x;
    vLight = uCross ? 1 : face == 2 ? 1 : face == 3 ? .55 : face < 2 ? .8 : .65;
    gl_Position = uVP * vec4(uOffset + point, 1);
}
