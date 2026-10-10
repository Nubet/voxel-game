package com.norbertfila.meshing;

import java.util.Objects;

public record MeshFormat(int version, int stride, int vertexCount, Bounds bounds, Topology topology) {
    public MeshFormat {
        if (version < 1) throw new IllegalArgumentException("Format version must be positive");
        if (stride < 1) throw new IllegalArgumentException("Stride must be positive");
        if (vertexCount < 0) throw new IllegalArgumentException("Vertex count must not be negative");
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(topology);
        topology.validateVertexCount(vertexCount);
    }

    public static MeshFormat floatPositionColor(float[] vertices) {
        Objects.requireNonNull(vertices);
        if (vertices.length % MeshData.VERTEX_STRIDE != 0) {
            throw new IllegalArgumentException("Vertex data must use stride " + MeshData.VERTEX_STRIDE);
        }
        int vertexCount = vertices.length / MeshData.VERTEX_STRIDE;
        Topology topology =
                vertexCount % Topology.TRIANGLES.verticesPerPrimitive == 0 ? Topology.TRIANGLES : Topology.POINTS;
        return new MeshFormat(1, MeshData.VERTEX_STRIDE, vertexCount, Bounds.from(vertices), topology);
    }

    public void validate(float[] vertices) {
        Objects.requireNonNull(vertices);
        if (vertices.length % stride != 0) throw new IllegalArgumentException("Vertex data must use stride " + stride);
        if (vertices.length / stride != vertexCount)
            throw new IllegalArgumentException("Vertex count does not match mesh format");
    }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public Bounds {
            if (!Float.isFinite(minX)
                    || !Float.isFinite(minY)
                    || !Float.isFinite(minZ)
                    || !Float.isFinite(maxX)
                    || !Float.isFinite(maxY)
                    || !Float.isFinite(maxZ)) {
                throw new IllegalArgumentException("Bounds must be finite");
            }
            if (minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("Bounds minimum exceeds maximum");
        }

        static Bounds from(float[] vertices) {
            if (vertices.length == 0) return new Bounds(0, 0, 0, 0, 0, 0);
            float minX = vertices[0], minY = vertices[1], minZ = vertices[2];
            float maxX = minX, maxY = minY, maxZ = minZ;
            for (int i = MeshData.VERTEX_STRIDE; i < vertices.length; i += MeshData.VERTEX_STRIDE) {
                minX = Math.min(minX, vertices[i]);
                minY = Math.min(minY, vertices[i + 1]);
                minZ = Math.min(minZ, vertices[i + 2]);
                maxX = Math.max(maxX, vertices[i]);
                maxY = Math.max(maxY, vertices[i + 1]);
                maxZ = Math.max(maxZ, vertices[i + 2]);
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public enum Topology {
        POINTS(1),
        LINES(2),
        TRIANGLES(3);

        private final int verticesPerPrimitive;

        Topology(int verticesPerPrimitive) {
            this.verticesPerPrimitive = verticesPerPrimitive;
        }

        private void validateVertexCount(int vertexCount) {
            if (vertexCount % verticesPerPrimitive != 0)
                throw new IllegalArgumentException("Vertex count does not match topology");
        }
    }
}
