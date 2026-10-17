#version 460 core

layout(location = 0) in vec3 aPosition;
uniform mat4 uVP;
uniform mat4 uModel;
uniform vec3 uMin;
uniform vec3 uSize;
out vec3 vLocal;

void main() {
    vLocal = aPosition;
    gl_Position = uVP * uModel * vec4(uMin + aPosition * uSize, 1.0);
}
