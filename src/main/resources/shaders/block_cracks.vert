#version 460 core

uniform mat4 uVP;
uniform vec3 uOffset;
uniform float uHeight;
uniform vec2 uWidth;
out vec2 vUv;
flat out int vFace;

const vec2 CORNERS[6] = vec2[](vec2(0, 0), vec2(1, 0), vec2(1, 1), vec2(1, 1), vec2(0, 1), vec2(0, 0));

void main() {
    vUv = CORNERS[gl_VertexID % 6];
    vFace = gl_VertexID / 6;
    vec3 point;
    if (vFace == 0) point = vec3(0, vUv.y, vUv.x);
    else if (vFace == 1) point = vec3(1, vUv.y, vUv.x);
    else if (vFace == 2) point = vec3(vUv.x, 1, vUv.y);
    else if (vFace == 3) point = vec3(vUv.x, 0, vUv.y);
    else if (vFace == 4) point = vec3(vUv.x, vUv.y, 0);
    else point = vec3(vUv.x, vUv.y, 1);
    point = (point - .5) * 1.002 + .5;
    point *= vec3(uWidth.x, uHeight, uWidth.y);
    gl_Position = uVP * vec4(uOffset + point, 1);
}
