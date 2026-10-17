#version 460 core

// Rebuilds six vertices per far terrain record; see QuadMesh for the bit layout.
layout(std430, binding = 0) readonly buffer Quads {
    uint quads[];
};

// Tile origin of each draw in chunk units, indexed by the base instance.
layout(std430, binding = 1) readonly buffer Draws {
    ivec4 chunks[];
};

uniform mat4 uVP;
uniform ivec3 uCameraBlock;
uniform vec3 uCameraFraction;

out vec2 vUv;
flat out float vLayer;
flat out float vTint;
out float vShade;
flat out vec2 vFlow;
out vec3 vCameraOffset;

const ivec3 FACE_U[6] = ivec3[](ivec3(0, 0, 1), ivec3(0, 0, -1), ivec3(0, 0, -1), ivec3(0, 0, 1), ivec3(-1, 0, 0), ivec3(1, 0, 0));
const ivec3 FACE_V[6] = ivec3[](ivec3(0, 1, 0), ivec3(0, 1, 0), ivec3(1, 0, 0), ivec3(1, 0, 0), ivec3(0, 1, 0), ivec3(0, 1, 0));
const float SHADE[6] = float[](0.77, 0.77, 1.0, 0.56, 0.86, 0.86);
const int FRONT[6] = int[](0, 1, 2, 0, 2, 3);
// Water surfaces sit below the top of their block, as near water does.
const float WATER_DROP = 0.125;

void main() {
    uint quad = uint(gl_VertexID) / 6u;
    int vertex = gl_VertexID % 6;
    uint shape = quads[quad * 3u];
    int y = int(quads[quad * 3u + 1u]);
    uint extent = quads[quad * 3u + 2u];
    int face = int((shape >> 14) & 7u);
    bool water = ((shape >> 17) & 1u) != 0u;
    int width = int((shape >> 18) & 63u) + 1;
    int height = int(extent & 0xFFFFu) + 1;
    ivec3 base = ivec3(shape & 127u, 0, (shape >> 7) & 127u);
    int corner = FRONT[vertex];
    bool alongV = corner == 1 || corner == 2;
    bool alongU = corner >= 2;
    ivec3 local = base + (alongV ? FACE_V[face] * height : ivec3(0)) + (alongU ? FACE_U[face] * width : ivec3(0));
    vUv = vec2(alongU ? width : 0, alongV ? height : 0);
    vShade = SHADE[face];
    vLayer = float((extent >> 16) & 255u);
    vTint = float((extent >> 24) & 31u);
    vFlow = vec2(0.0);
    ivec3 origin = chunks[gl_BaseInstance].xyz * 16 - uCameraBlock;
    // The world y can be far from the camera; subtracting as integers first keeps it exact.
    vec3 relative = vec3(origin.x + local.x, y - uCameraBlock.y + local.y, origin.z + local.z) - uCameraFraction;
    if (water) relative.y -= WATER_DROP;
    if (((shape >> 24) & 1u) != 0u) relative.y -= 0.875;
    vCameraOffset = relative;
    gl_Position = uVP * vec4(relative, 1.0);
}
