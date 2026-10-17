#version 460 core

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUV;
uniform mat4 uVP;
uniform mat4 uModel;
out vec2 vUV;

void main() {
    vUV = aUV;
    gl_Position = uVP * uModel * vec4(aPosition, 1.0);
}
