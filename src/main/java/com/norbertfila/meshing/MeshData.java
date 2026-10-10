package com.norbertfila.meshing;

/** Immutable CPU mesh data with interleaved x,y,z,r,g,b floats. */
public record MeshData(float[] vertices) {
    public static final int VERTEX_STRIDE = 6;
    public static final int VERTICES_PER_FACE = 6;

    public MeshData {
        if (vertices == null) {
            throw new NullPointerException("vertices");
        }
        if (vertices.length % VERTEX_STRIDE != 0) {
            throw new IllegalArgumentException("Vertex data must use stride " + VERTEX_STRIDE);
        }
        vertices = vertices.clone();
    }

    @Override
    public float[] vertices() {
        return vertices.clone();
    }

    public int vertexCount() {
        return vertices.length / VERTEX_STRIDE;
    }

    public int faceCount() {
        return vertexCount() / VERTICES_PER_FACE;
    }
}
