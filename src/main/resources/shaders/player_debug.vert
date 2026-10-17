#version 460 core

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUV;
uniform mat4 uVP;
uniform mat4 uModel;
out vec2 vUV;
out vec3 vCameraOffset;
out float vShade;

// CuboidMesh emits six consecutive vertices per face in this order.
const vec3 NORMALS[6] = vec3[6](vec3(0,0,-1), vec3(0,0,1), vec3(1,0,0),
    vec3(-1,0,0), vec3(0,1,0), vec3(0,-1,0));

void main() {
    vUV = aUV;
    vec3 normal = normalize(transpose(inverse(mat3(uModel))) * NORMALS[(gl_VertexID / 6) % 6]);
    vec3 squared = normal * normal;
    vShade = dot(squared, vec3(.77, normal.y > 0.0 ? 1.0 : .56, .86));
    vec4 position = uModel * vec4(aPosition, 1.0);
    vCameraOffset = position.xyz;
    gl_Position = uVP * position;
}
