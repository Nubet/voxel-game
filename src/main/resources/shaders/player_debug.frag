#version 460 core

in vec3 vLocal;
uniform vec3 uColor;
out vec4 fragColor;

void main() {
    vec3 normal = normalize(cross(dFdx(vLocal), dFdy(vLocal)));
    float shade = 0.65 + 0.35 * max(dot(normal, normalize(vec3(0.4, 0.8, -0.6))), 0.0);
    fragColor = vec4(uColor * shade, 1.0);
}
