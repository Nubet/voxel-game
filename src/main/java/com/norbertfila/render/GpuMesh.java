package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.glDeleteBuffers;
import static org.lwjgl.opengl.GL46C.glDeleteVertexArrays;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.meshing.MeshFormat;
import com.norbertfila.meshing.MeshRanges;

public final class GpuMesh implements AutoCloseable {
    interface Deleter {
        void delete(int vao, int vbo);
    }

    private final int vao;
    private final int vbo;
    private final MeshFormat format;
    private final MeshRanges ranges;
    private final Deleter deleter;
    private boolean closed;

    GpuMesh(int vao, int vbo, int vertexCount) {
        this(vao, vbo, legacyFormat(vertexCount), new MeshRanges(vertexCount, 0, 0), (array, buffer) -> {
            glDeleteBuffers(buffer);
            glDeleteVertexArrays(array);
        });
    }

    GpuMesh(int vao, int vbo, int vertexCount, Deleter deleter) {
        this(vao, vbo, legacyFormat(vertexCount), new MeshRanges(vertexCount, 0, 0), deleter);
    }

    private GpuMesh(int vao, int vbo, MeshFormat format, MeshRanges ranges, Deleter deleter) {
        this.vao = vao;
        this.vbo = vbo;
        this.format = format;
        this.ranges = ranges;
        this.deleter = deleter;
    }

    GpuMesh(int vao, int vbo, MeshData mesh, Deleter deleter) {
        this(vao, vbo, mesh.format(), mesh.ranges(), deleter);
    }

    int vao() {
        return vao;
    }

    int vertexCount() {
        return format.vertexCount();
    }

    private static MeshFormat legacyFormat(int vertexCount) {
        return new MeshFormat(
                1,
                MeshData.VERTEX_STRIDE,
                vertexCount,
                new MeshFormat.Bounds(0, 0, 0, 0, 0, 0),
                MeshFormat.Topology.TRIANGLES);
    }

    MeshFormat format() {
        return format;
    }

    MeshRanges ranges() {
        return ranges;
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
