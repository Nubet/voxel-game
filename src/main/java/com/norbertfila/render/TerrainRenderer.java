package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/** Intentionally tiny OpenGL backend. Resource ownership remains explicit. */
public final class TerrainRenderer implements AutoCloseable {
    private final List<GpuMesh> meshes = new ArrayList<>();
    private final int program, matrixUniform;

    public TerrainRenderer() {
        int vertex = compile(
                GL_VERTEX_SHADER,
                "#version 330 core\nlayout(location=0) in vec3 position;layout(location=1) in vec3 color;uniform mat4 uVP;out vec3 vColor;void main(){vColor=color;gl_Position=uVP*vec4(position,1.0);}");
        int fragment = compile(
                GL_FRAGMENT_SHADER,
                "#version 330 core\nin vec3 vColor;out vec4 fragColor;void main(){fragColor=vec4(vColor,1.0);}");
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new IllegalStateException(glGetProgramInfoLog(program));
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        matrixUniform = glGetUniformLocation(program, "uVP");
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException(glGetShaderInfoLog(shader));
        return shader;
    }

    public void upload(MeshData mesh) {
        if (mesh.vertexCount() == 0) return;
        int vao = glGenVertexArrays(), vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
        meshes.add(new GpuMesh(vao, vbo, mesh.vertexCount()));
    }

    public void render(Matrix4f viewProjection) {
        glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer mat = stack.mallocFloat(16);
            viewProjection.get(mat);
            glUniformMatrix4fv(matrixUniform, false, mat);
        }
        for (GpuMesh mesh : meshes) {
            glBindVertexArray(mesh.vao);
            glDrawArrays(GL_TRIANGLES, 0, mesh.count);
        }
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        for (GpuMesh mesh : meshes) {
            glDeleteBuffers(mesh.vbo);
            glDeleteVertexArrays(mesh.vao);
        }
        glDeleteProgram(program);
    }

    private record GpuMesh(int vao, int vbo, int count) {}
}
