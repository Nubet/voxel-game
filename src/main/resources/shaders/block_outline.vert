#version 460 core

layout(location = 0) in vec3 aPosition;

uniform mat4 uVP;
uniform vec3 uOffset;
uniform float uHeight;
uniform vec2 uWidth;

void main() {
    gl_Position = uVP * vec4(aPosition * vec3(uWidth.x, uHeight, uWidth.y) + uOffset, 1.0);
}
