#version 330 core

layout(location = 0) in vec2 position;
layout(location = 1) in vec4 color;

uniform vec2 uViewport;

out vec4 vColor;

void main() {
    vec2 ndc = vec2(position.x / uViewport.x * 2.0 - 1.0, 1.0 - position.y / uViewport.y * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
    vColor = color;
}
