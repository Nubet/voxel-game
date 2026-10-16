#version 460 core

layout(location = 0) in vec3 aPosition;

uniform mat4 uVP;
uniform vec3 uOffset;
uniform float uHeight;

void main() {
    gl_Position = uVP * vec4(vec3(aPosition.x, aPosition.y * uHeight, aPosition.z) + uOffset, 1.0);
}
