package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.glDeleteBuffers;
import static org.lwjgl.opengl.GL46C.glDeleteVertexArrays;

public final class GpuMesh implements AutoCloseable {
    interface Deleter {
        void delete(int vao, int vbo);
    }

    private final int vao;
    private final int vbo;
    private final int vertexCount;
    private final Deleter deleter;
    private boolean closed;

    GpuMesh(int vao, int vbo, int vertexCount) {
        this(vao, vbo, vertexCount, (array, buffer) -> {
            glDeleteBuffers(buffer);
            glDeleteVertexArrays(array);
        });
    }

    GpuMesh(int vao, int vbo, int vertexCount, Deleter deleter) {
        this.vao = vao;
        this.vbo = vbo;
        this.vertexCount = vertexCount;
        this.deleter = deleter;
    }

    int vao() {
        return vao;
    }

    int vertexCount() {
        return vertexCount;
    }

    boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        deleter.delete(vao, vbo);
    }
}
