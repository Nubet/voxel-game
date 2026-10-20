#version 460 core

struct Column { vec4 position; vec4 weather; };
layout(std430, binding = 4) readonly buffer Columns { Column columns[]; };
uniform mat4 uVP;
uniform vec3 uOffset;
uniform float uSeconds;
uniform float uBase;
uniform float uIntensity;
out vec2 vUV;
out vec3 vPosition;
out float vHeight;
out float vSurface;
out float vAmount;
flat out int vSnow;

float random(float seed) { return fract(sin(seed * 127.1 + 311.7) * 43758.5453); }

void main() {
    const vec2 CORNERS[6] = vec2[](vec2(0,0), vec2(1,0), vec2(1,1), vec2(0,0), vec2(1,1), vec2(0,1));
    Column column = columns[gl_InstanceID / 16];
    int particle = gl_InstanceID % 16;
    vSnow = particle >= 8 ? 1 : 0;
    float seed = column.position.w * 100.0 + float(particle) * 13.37;
    vec2 jitter = vec2(random(seed), random(seed + 1.0)) * 0.6 + 0.2;
    float speed = vSnow == 1 ? 2.0 : 16.0;
    float y = uBase + mod(random(seed + 2.0) * 32.0 - uSeconds * speed - uBase, 32.0);
    float drift = vSnow == 1 ? sin(uSeconds * 0.7853981634 + seed) * 0.1 : 0.0;
    vec3 center = vec3(column.position.x + jitter.x + drift, y, column.position.z + jitter.y) + uOffset;
    vec2 facing = center.xz;
    vec2 horizontal = length(facing) < 0.001 ? vec2(1,0) : normalize(vec2(facing.y, -facing.x));
    vec3 right = vec3(horizontal.x, 0, horizontal.y);
    vec2 uv = CORNERS[gl_VertexID];
    float width = vSnow == 1 ? 0.24 : 0.3;
    float height = vSnow == 1 ? 0.24 : 1.0;
    vec3 position = center + right * ((uv.x - 0.5) * width) + vec3(0, (uv.y - 0.5) * height, 0);
    vPosition = position;
    vHeight = position.y - uOffset.y;
    vSurface = column.position.y;
    float local = vSnow == 1 ? column.weather.y : column.weather.x;
    float amount = clamp(local * uIntensity, 0.0, 1.0);
    float threshold = random(seed + 3.0) * 0.9;
    vAmount = smoothstep(threshold, threshold + 0.1, amount);
    vUV = uv;
    gl_Position = uVP * vec4(position, 1);
}
