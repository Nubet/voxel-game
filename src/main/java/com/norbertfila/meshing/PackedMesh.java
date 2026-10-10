package com.norbertfila.meshing;

import java.util.Objects;

public record PackedMesh(int[] vertices, int faceCount) {
    public PackedMesh {
        Objects.requireNonNull(vertices);
        if (vertices.length % MeshData.VERTICES_PER_FACE != 0)
            throw new IllegalArgumentException("Packed mesh must contain complete faces");
        if (faceCount != vertices.length / MeshData.VERTICES_PER_FACE)
            throw new IllegalArgumentException("Invalid packed face count");
        vertices = vertices.clone();
    }

    @Override
    public int[] vertices() {
        return vertices.clone();
    }

    public int vertexCount() {
        return vertices.length;
    }

    public int bytes() {
        return vertices.length * PackedVertex.BYTES;
    }
}
