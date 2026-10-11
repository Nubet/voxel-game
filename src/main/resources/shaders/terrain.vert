#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec4 attributeData;

uniform mat4 uVP;

out vec3 vColor;
out vec2 vUv;
flat out float vLayer;
out float vShade;

void main() {
    vColor = attributeData.rgb;
    vUv = attributeData.xy;
    vLayer = attributeData.z;
    vShade = attributeData.w;
    gl_Position = uVP * vec4(position, 1.0);
}
