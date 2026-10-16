#version 460 core

uniform mat4 uVP;
uniform float uHeight;
uniform float uRadius;

out vec2 vOffset;

const vec2 CORNERS[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1),
                             vec2(-1, -1), vec2(1, 1), vec2(-1, 1));

void main() {
    vOffset = CORNERS[gl_VertexID] * uRadius;
    gl_Position = uVP * vec4(vOffset.x, uHeight, vOffset.y, 1.0);
}
