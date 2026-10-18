#version 460 core

// Rebuilds six vertices per water quad record; see QuadMesh for the bit layout and QuadCodec for the CPU twin.
layout(std430, binding = 0) readonly buffer Quads {
    uint quads[];
};

// Chunk coordinates of each draw, indexed by the base instance both draw paths pass.
layout(std430, binding = 1) readonly buffer Draws {
    ivec4 chunks[];
};

uniform mat4 uVP;
uniform int uRenderLayer;
// The camera is split into an integer block and a fraction so origins are exact far from the world origin.
uniform ivec3 uCameraBlock;
uniform vec3 uCameraFraction;

out vec2 vUv;
flat out float vLayer;
flat out float vTint;
out float vShade;
flat out vec2 vFlow;
out vec3 vCameraOffset;

const ivec3 CORNERS[24] = ivec3[](
    ivec3(1, 0, 0), ivec3(1, 1, 0), ivec3(1, 1, 1), ivec3(1, 0, 1),
    ivec3(0, 0, 1), ivec3(0, 1, 1), ivec3(0, 1, 0), ivec3(0, 0, 0),
    ivec3(0, 1, 1), ivec3(1, 1, 1), ivec3(1, 1, 0), ivec3(0, 1, 0),
    ivec3(0, 0, 0), ivec3(1, 0, 0), ivec3(1, 0, 1), ivec3(0, 0, 1),
    ivec3(1, 0, 1), ivec3(1, 1, 1), ivec3(0, 1, 1), ivec3(0, 0, 1),
    ivec3(0, 0, 0), ivec3(0, 1, 0), ivec3(1, 1, 0), ivec3(1, 0, 0));
const float SHADE[6] = float[](0.77, 0.77, 1.0, 0.56, 0.86, 0.86);
const int FRONT[6] = int[](0, 1, 2, 0, 2, 3);

void main() {
    vTint = -1.0;
    uint quad = uint(gl_VertexID) / 6u;
    int vertex = gl_VertexID % 6;
    uint cell = quads[quad * 4u];
    uint low = quads[quad * 4u + 1u];
    uint high = quads[quad * 4u + 2u];
    uint flow = quads[quad * 4u + 3u];
    ivec3 position = ivec3(cell & 15u, (cell >> 4) & 15u, (cell >> 8) & 15u);
    int face = int((cell >> 12) & 7u);
    bool falling = ((cell >> 15) & 1u) != 0u;
    vec2 h00h10 = unpackUnorm2x16(low);
    vec2 h11h01 = unpackUnorm2x16(high);
    ivec3 corner = CORNERS[face * 4 + FRONT[vertex]];
    float h = corner.y == 0 ? 0.0 : corner.x == 0 ? (corner.z == 0 ? h00h10.x : h11h01.y) : (corner.z == 0 ? h00h10.y : h11h01.x);
    vec3 local = vec3(position.x + corner.x, float(position.y) + h, position.z + corner.z);
    vUv = face == 2 || face == 3 ? vec2(corner.x, corner.z) : vec2(face < 2 ? corner.z : corner.x, h);
    vLayer = float(cell >> 16);
    vShade = uRenderLayer == 0 ? 1.0 : SHADE[face];
    vFlow = face == 2 ? unpackSnorm2x16(flow) : vec2(0.0, falling ? -1.0 : 0.0);
    vec3 chunkOrigin = vec3(chunks[gl_BaseInstance].xyz * 16 - uCameraBlock) - uCameraFraction;
    vCameraOffset = chunkOrigin + local;
    gl_Position = uVP * vec4(vCameraOffset, 1.0);
}
