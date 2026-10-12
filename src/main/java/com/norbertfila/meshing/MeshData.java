package com.norbertfila.meshing;

import java.util.Objects;

public record MeshData(MeshFormat format, float[] vertices, MeshRanges ranges) {
    public static final int VERTEX_STRIDE = 6;
    public static final int VERTICES_PER_FACE = 6;

    public MeshData(float[] vertices) {
        this(MeshFormat.floatPositionColor(vertices), vertices);
    }

    public MeshData(MeshFormat format, float[] vertices) {
        this(format, vertices, new MeshRanges(format.vertexCount(), 0, 0));
    }

    public static MeshData textured(float[] vertices) {
        return new MeshData(MeshFormat.texturedPositionUvLayerShade(vertices), vertices);
    }

    public static MeshData textured(float[] vertices, MeshRanges ranges) {
        return new MeshData(MeshFormat.texturedPositionUvLayerShade(vertices), vertices, ranges);
    }

    public MeshData {
        Objects.requireNonNull(format);
        Objects.requireNonNull(vertices);
        Objects.requireNonNull(ranges);
        format.validate(vertices);
        ranges.validate(format);
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
