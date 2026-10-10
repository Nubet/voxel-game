package com.norbertfila.meshing;

import java.util.Objects;

public record MeshData(MeshFormat format, float[] vertices) {
    public static final int VERTEX_STRIDE = 6;
    public static final int VERTICES_PER_FACE = 6;

    public MeshData(float[] vertices) {
        this(MeshFormat.floatPositionColor(vertices), vertices);
    }

    public MeshData {
        Objects.requireNonNull(format);
        Objects.requireNonNull(vertices);
        format.validate(vertices);
        vertices = vertices.clone();
    }

    @Override
    public float[] vertices() {
        return vertices.clone();
    }

    public int vertexCount() {
        return format.vertexCount();
    }

    public int faceCount() {
        return vertexCount() / VERTICES_PER_FACE;
    }
}
