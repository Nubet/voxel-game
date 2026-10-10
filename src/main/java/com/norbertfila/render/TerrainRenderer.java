package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/** Intentionally tiny OpenGL backend. Resource ownership remains explicit. */
public final class TerrainRenderer implements AutoCloseable {
    private final Map<ChunkPos, GpuMesh> meshes = new HashMap<>();
    private final int matrixUniform;
    private int program;
    private boolean closed;

    public TerrainRenderer() {
        int vertex = 0;
        int fragment = 0;
        int linkedProgram = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, "/shaders/terrain.vert");
            fragment = compile(GL_FRAGMENT_SHADER, "/shaders/terrain.frag");
            linkedProgram = glCreateProgram();
            glAttachShader(linkedProgram, vertex);
            glAttachShader(linkedProgram, fragment);
            glLinkProgram(linkedProgram);
            if (glGetProgrami(linkedProgram, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException("Shader program link failed: " + glGetProgramInfoLog(linkedProgram));
            }
            int uniform = glGetUniformLocation(linkedProgram, "uVP");
            if (uniform < 0) {
                throw new IllegalStateException("Shader uniform not found: uVP");
            }
            program = linkedProgram;
            matrixUniform = uniform;
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        } catch (RuntimeException | Error failure) {
            if (linkedProgram != 0) {
                glDeleteProgram(linkedProgram);
            }
            throw failure;
        } finally {
            if (vertex != 0) {
                glDeleteShader(vertex);
            }
            if (fragment != 0) {
                glDeleteShader(fragment);
            }
        }
    }

    private static int compile(int type, String resource) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, loadSource(resource));
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new IllegalStateException(
                        "Shader compilation failed for " + resource + ": " + glGetShaderInfoLog(shader));
            }
            return shader;
        } catch (RuntimeException | Error failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }

    private static String loadSource(String resource) {
        try (InputStream stream = TerrainRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Shader resource not found: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read shader resource: " + resource, failure);
        }
    }

    public void upload(ChunkPos position, MeshData mesh) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(mesh);
        if (mesh.vertexCount() == 0) {
            GpuMesh previous = meshes.remove(position);
            if (previous != null) previous.close();
            return;
        }
        int vao = glGenVertexArrays(), vbo = glGenBuffers();
        try {
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, mesh.vertices(), GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);
            glEnableVertexAttribArray(1);
            glBindVertexArray(0);
            GpuMesh previous = meshes.put(position, new GpuMesh(vao, vbo, mesh.vertexCount()));
            if (previous != null) previous.close();
        } catch (RuntimeException | Error failure) {
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            throw failure;
        }
    }

    public void render(Matrix4f viewProjection) {
        glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer mat = stack.mallocFloat(16);
            viewProjection.get(mat);
            glUniformMatrix4fv(matrixUniform, false, mat);
        }
        for (GpuMesh mesh : meshes.values()) {
            glBindVertexArray(mesh.vao());
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount());
        }
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        meshes.values().forEach(GpuMesh::close);
        glDeleteProgram(program);
        meshes.clear();
    }
}
