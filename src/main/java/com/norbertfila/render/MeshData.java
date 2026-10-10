package com.norbertfila.render;

/** Interleaved x,y,z,r,g,b floats. GPU-agnostic, disposable intermediate form */
public record MeshData(float[] vertices) {
    public int vertexCount() {
        return vertices.length / 6;
    }
}
