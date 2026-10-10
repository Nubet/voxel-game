package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.meshing.PackedMesh;
import com.norbertfila.meshing.PackedVertex;
import com.norbertfila.world.ChunkPos;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class PackedTerrainRenderer implements AutoCloseable {
    private final Map<ChunkPos, PackedGpuMesh> meshes = new HashMap<>();
    private final int matrixUniform;
    private final int chunkOriginUniform;
    private int program;
    private boolean closed;

    public PackedTerrainRenderer() {
        int vertex = 0;
        int fragment = 0;
        int linkedProgram = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, "/shaders/packed_terrain.vert");
            fragment = compile(GL_FRAGMENT_SHADER, "/shaders/terrain.frag");
            linkedProgram = glCreateProgram();
            glAttachShader(linkedProgram, vertex);
            glAttachShader(linkedProgram, fragment);
            glLinkProgram(linkedProgram);
            if (glGetProgrami(linkedProgram, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException(
                        "Packed shader program link failed: " + glGetProgramInfoLog(linkedProgram));
            }
            int uniform = glGetUniformLocation(linkedProgram, "uVP");
            if (uniform < 0) throw new IllegalStateException("Packed shader uniform not found: uVP");
            int originUniform = glGetUniformLocation(linkedProgram, "uChunkOrigin");
            if (originUniform < 0) throw new IllegalStateException("Packed shader uniform not found: uChunkOrigin");
            program = linkedProgram;
            matrixUniform = uniform;
            chunkOriginUniform = originUniform;
        } catch (RuntimeException | Error failure) {
            if (linkedProgram != 0) glDeleteProgram(linkedProgram);
            throw failure;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String resource) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, loadSource(resource));
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new IllegalStateException("Packed shader compilation failed: " + glGetShaderInfoLog(shader));
            }
            return shader;
        } catch (RuntimeException | Error failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }

    private static String loadSource(String resource) {
        try (InputStream stream = PackedTerrainRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Shader resource not found: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read shader resource: " + resource, failure);
        }
    }

    public void upload(ChunkPos position, PackedMesh mesh) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(mesh);
        PackedGpuMesh previous = meshes.remove(position);
        if (mesh.vertexCount() == 0) {
            if (previous != null) previous.close();
            return;
        }
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        try {
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer data = stack.mallocInt(mesh.vertexCount());
                data.put(mesh.vertices()).flip();
                glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
            }
            glVertexAttribIPointer(0, 1, GL_UNSIGNED_INT, PackedVertex.BYTES, 0);
            glEnableVertexAttribArray(0);
            glBindVertexArray(0);
            meshes.put(position, new PackedGpuMesh(vao, vbo, mesh.vertexCount()));
            if (previous != null) previous.close();
        } catch (RuntimeException | Error failure) {
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            if (previous != null) meshes.put(position, previous);
            throw failure;
        }
    }

    public void render(Matrix4f viewProjection, Frustum frustum) {
        glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            viewProjection.get(matrix);
            glUniformMatrix4fv(matrixUniform, false, matrix);
        }
        for (Map.Entry<ChunkPos, PackedGpuMesh> entry : meshes.entrySet()) {
            if (!frustum.intersects(entry.getKey())) continue;
            ChunkPos position = entry.getKey();
            glUniform3i(chunkOriginUniform, position.x() * 16, position.y() * 16, position.z() * 16);
            glBindVertexArray(entry.getValue().vao());
            glDrawArrays(GL_TRIANGLES, 0, entry.getValue().vertexCount());
        }
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        meshes.values().forEach(PackedGpuMesh::close);
        meshes.clear();
        glDeleteProgram(program);
    }

    private static final class PackedGpuMesh implements AutoCloseable {
        private final int vao;
        private final int vbo;
        private final int vertexCount;

        PackedGpuMesh(int vao, int vbo, int vertexCount) {
            this.vao = vao;
            this.vbo = vbo;
            this.vertexCount = vertexCount;
        }

        int vao() {
            return vao;
        }

        int vertexCount() {
            return vertexCount;
        }

        @Override
        public void close() {
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
        }
    }
}
