#version 460 core

uniform mat4 uVP;
uniform vec3 uOffset;
uniform float uAngle;
uniform ivec3 uLayers;
uniform int uShape;
uniform bool uItemTexture;
uniform float uHeight;
// Held items are placed over unit space by uModel instead of spinning at a world offset.
uniform bool uHeld;
uniform mat4 uModel;
uniform sampler2DArray uBlockTextures;
uniform sampler2DArray uItemTextures;
out vec2 vUv;
flat out int vLayer;
flat out float vLight;

const int CUBE = 0, CROSS = 1, FLAT = 2, EXTRUDED = 3;
const vec2 CORNERS[6] = vec2[](vec2(0, 0), vec2(1, 0), vec2(1, 1), vec2(1, 1), vec2(0, 1), vec2(0, 0));

vec3 box(int face, vec2 uv) {
    if (face == 0) return vec3(0, uv.y, uv.x);
    if (face == 1) return vec3(1, uv.y, uv.x);
    if (face == 2) return vec3(uv.x, 1, uv.y);
    if (face == 3) return vec3(uv.x, 0, uv.y);
    if (face == 4) return vec3(uv.x, uv.y, 0);
    return vec3(uv.x, uv.y, 1);
}

float shade(int face) {
    return face == 2 ? 1 : face == 3 ? .55 : face < 2 ? .8 : .65;
}

void main() {
    vec2 uv = CORNERS[gl_VertexID % 6];
    int face = (gl_VertexID / 6) % 6;
    vec3 point;
    vLayer = uLayers.x;
    if (uShape == EXTRUDED) {
        // One pixel-sized box per texel, like Minecraft's generated item models; transparent texels collapse.
        ivec2 pixel = ivec2(gl_VertexID / 36 % 16, gl_VertexID / 36 / 16);
        float alpha = uItemTexture
                ? texelFetch(uItemTextures, ivec3(pixel, vLayer), 0).a
                : texelFetch(uBlockTextures, ivec3(pixel, vLayer), 0).a;
        if (alpha < .1) {
            gl_Position = vec4(0);
            return;
        }
        point = (vec3(pixel.x, 15 - pixel.y, 7.5) + box(face, uv)) / 16;
        vUv = (vec2(pixel) + .5) / 16;
        vLight = face >= 4 ? 1 : shade(face);
    } else if (uShape == FLAT) {
        point = vec3(uv, .5);
        vUv = vec2(uv.x, 1 - uv.y);
        vLight = 1;
    } else if (uShape == CROSS) {
        point = gl_VertexID < 6 ? vec3(uv.x, uv.y, uv.x) : vec3(uv.x, uv.y, 1 - uv.x);
        vUv = vec2(uv.x, 1 - uv.y);
        vLight = 1;
    } else {
        point = box(face, uv);
        vUv = face == 2 || face == 3 ? vec2(uv.x, 1 - uv.y) : uv;
        vLayer = face == 2 ? uLayers.y : face == 3 ? uLayers.z : uLayers.x;
        vLight = shade(face);
    }
    vec3 position;
    if (uHeld) {
        point.y *= uHeight;
        position = (uModel * vec4(point, 1)).xyz;
    } else {
        point = (point - .5) * (uShape >= FLAT ? .5 : .25);
        point.y *= uHeight;
        float c = cos(uAngle), s = sin(uAngle);
        point.xz = mat2(c, s, -s, c) * point.xz;
        position = uOffset + point;
    }
    gl_Position = uVP * vec4(position, 1);
}
