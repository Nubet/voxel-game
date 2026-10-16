#version 460 core

uniform mat4 uVP;
uniform float uHeight;
uniform float uRadius;
uniform bool uFancy;
uniform vec2 uPhase;
uniform float uPeriod;

layout(std430, binding = 3) readonly buffer CloudMesh {
    vec4 vertices[];
};

out vec2 vOffset;
out float vShade;

const vec2 CORNERS[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1),
                             vec2(-1, -1), vec2(1, 1), vec2(-1, 1));

void main() {
    vec3 position;
    if (uFancy) {
        vec4 vertex = vertices[gl_VertexID];
        vec2 tile = vec2(gl_InstanceID % 3 - 1, gl_InstanceID / 3 - 1) * uPeriod;
        vOffset = vertex.xz + tile - uPhase;
        vShade = vertex.w;
        position = vec3(vOffset.x, uHeight + vertex.y, vOffset.y);
    } else {
        vOffset = CORNERS[gl_VertexID] * uRadius;
        vShade = 1.0;
        position = vec3(vOffset.x, uHeight, vOffset.y);
    }
    gl_Position = uVP * vec4(position, 1.0);
}
