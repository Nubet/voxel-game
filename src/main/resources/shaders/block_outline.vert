#version 460 core

layout(location = 0) in vec3 aPosition;

uniform mat4 uVP;
uniform vec3 uOffset;

void main() {
    gl_Position = uVP * vec4(aPosition + uOffset, 1.0);
}
