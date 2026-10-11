#version 330 core

layout(location = 0) in uint packedVertex;

uniform mat4 uVP;
uniform ivec3 uChunkOrigin;

out vec3 vColor;
out vec2 vUv;
flat out float vLayer;
out float vShade;

const int U_AXIS[6] = int[](2, 2, 2, 2, 0, 0);
const int U_SIGN[6] = int[](1, -1, -1, 1, -1, 1);
const int V_AXIS[6] = int[](1, 1, 0, 0, 1, 1);

const ivec3 CORNERS[24] = ivec3[](
    ivec3(1, 0, 0), ivec3(1, 1, 0), ivec3(1, 1, 1), ivec3(1, 0, 1),
    ivec3(0, 0, 1), ivec3(0, 1, 1), ivec3(0, 1, 0), ivec3(0, 0, 0),
    ivec3(0, 1, 1), ivec3(1, 1, 1), ivec3(1, 1, 0), ivec3(0, 1, 0),
    ivec3(0, 0, 0), ivec3(1, 0, 0), ivec3(1, 0, 1), ivec3(0, 0, 1),
    ivec3(1, 0, 1), ivec3(1, 1, 1), ivec3(0, 1, 1), ivec3(0, 0, 1),
    ivec3(0, 0, 0), ivec3(0, 1, 0), ivec3(1, 1, 0), ivec3(1, 0, 0)
);

void main() {
    uint face = (packedVertex >> 12u) & 7u;
    uint block = (packedVertex >> 15u) & 255u;
    uint corner = (packedVertex >> 23u) & 3u;
    uint textureLayer = (packedVertex >> 25u) & 127u;
    ivec3 origin = ivec3(int(packedVertex & 15u), int((packedVertex >> 4u) & 15u), int((packedVertex >> 8u) & 15u));
    ivec3 local = origin + CORNERS[int(face) * 4 + int(corner)];
    ivec3 base = origin + CORNERS[int(face) * 4];
    vec3 color = block == 1u ? vec3(.50, .46, .27) : block == 2u ? vec3(.46, .29, .16) : vec3(.48, .49, .51);
    float shade = face == 2u ? 1.0 : face == 3u ? .56 : face < 2u ? .77 : .86;
    vColor = color * shade;
    vUv = vec2(
        float((local[U_AXIS[int(face)]] - base[U_AXIS[int(face)]]) * U_SIGN[int(face)]),
        float(local[V_AXIS[int(face)]] - base[V_AXIS[int(face)]])
    );
    vLayer = float(textureLayer);
    vShade = shade;
    gl_Position = uVP * vec4(local + uChunkOrigin, 1.0);
}
