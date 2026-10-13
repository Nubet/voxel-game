#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec4 attributeData;
layout(location = 2) in vec2 flow;

uniform mat4 uVP;

out vec3 vColor;
out vec2 vUv;
flat out float vLayer;
out float vShade;
flat out vec2 vFlow;

void main() {
    vColor = attributeData.rgb;
    vUv = attributeData.xy;
    vLayer = attributeData.z;
    vShade = attributeData.w;
    vFlow = flow;
    gl_Position = uVP * vec4(position, 1.0);
}
