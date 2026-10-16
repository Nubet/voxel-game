#version 460 core

// Rebuilds six vertices per solid quad record; see QuadMesh for the bit layout and QuadCodec for the CPU twin.
layout(std430, binding = 0) readonly buffer Quads {
    uint quads[];
};

// Chunk coordinates of each draw, indexed by the base instance both draw paths pass.
layout(std430, binding = 1) readonly buffer Draws {
    ivec4 chunks[];
};

uniform mat4 uVP;
// The camera is split into an integer block and a fraction so origins are exact far from the world origin.
uniform ivec3 uCameraBlock;
uniform vec3 uCameraFraction;

out vec2 vUv;
flat out float vLayer;
out float vShade;
flat out vec2 vFlow;
out vec3 vCameraOffset;

const ivec3 FACE_U[6] = ivec3[](ivec3(0, 0, 1), ivec3(0, 0, -1), ivec3(0, 0, -1), ivec3(0, 0, 1), ivec3(-1, 0, 0), ivec3(1, 0, 0));
const ivec3 FACE_V[6] = ivec3[](ivec3(0, 1, 0), ivec3(0, 1, 0), ivec3(1, 0, 0), ivec3(1, 0, 0), ivec3(0, 1, 0), ivec3(0, 1, 0));
const float SHADE[6] = float[](0.77, 0.77, 1.0, 0.56, 0.86, 0.86);
const int FRONT[6] = int[](0, 1, 2, 0, 2, 3);
const int BACK[6] = int[](2, 1, 0, 3, 2, 0);
const ivec4 CROSS_PLANES[2] = ivec4[](ivec4(0, 0, 1, 1), ivec4(1, 0, 0, 1));

void main() {
    uint quad = uint(gl_VertexID) / 6u;
    int vertex = gl_VertexID % 6;
    uint position = quads[quad * 2u];
    uint material = quads[quad * 2u + 1u];
    ivec3 base = ivec3(position & 31u, (position >> 5) & 31u, (position >> 10) & 31u);
    int face = int((position >> 15) & 7u);
    int width = int((position >> 18) & 15u) + 1;
    int height = int((position >> 22) & 15u) + 1;
    bool back = ((position >> 26) & 1u) != 0u;
    vec3 local;
    if (face < 6) {
        int corner = FRONT[vertex];
        bool alongV = corner == 1 || corner == 2;
        bool alongU = corner >= 2;
        local = vec3(base + (alongV ? FACE_V[face] * height : ivec3(0)) + (alongU ? FACE_U[face] * width : ivec3(0)));
        uint top = (material >> 24) & 7u;
        if (top != 0u && (face == 2 || (face != 3 && alongV))) local.y -= 1.0 - float(top) / 8.0;
        vUv = vec2(alongU ? width : 0, alongV ? height : 0);
        vShade = SHADE[face];
    } else {
        ivec4 plane = CROSS_PLANES[face - 6];
        int corner = back ? BACK[vertex] : FRONT[vertex];
        bool right = corner >= 2;
        int top = corner == 1 || corner == 2 ? 1 : 0;
        local = vec3(base + ivec3(right ? plane.z : plane.x, top, right ? plane.w : plane.y));
        vUv = vec2(right ? 1.0 : 0.0, float(1 - top));
        vShade = 1.0;
    }
    vLayer = float(material & 0xFFFFu);
    vFlow = vec2(0.0);
    vec3 chunkOrigin = vec3(chunks[gl_BaseInstance].xyz * 16 - uCameraBlock) - uCameraFraction;
    vCameraOffset = chunkOrigin + local;
    gl_Position = uVP * vec4(vCameraOffset, 1.0);
}
