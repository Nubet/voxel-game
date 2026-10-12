package com.norbertfila.meshing;

import com.norbertfila.world.RenderLayer;

public record MeshRanges(int opaqueVertices, int cutoutVertices, int translucentVertices) {
    public MeshRanges {
        if (opaqueVertices < 0 || cutoutVertices < 0 || translucentVertices < 0)
            throw new IllegalArgumentException("Layer vertex counts must be non-negative");
        if ((long) opaqueVertices + cutoutVertices + translucentVertices > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Layer vertex counts exceed supported buffer size");
    }

    public int firstVertex(RenderLayer layer) {
        return switch (layer) {
            case OPAQUE -> 0;
            case CUTOUT -> opaqueVertices;
            case TRANSLUCENT -> opaqueVertices + cutoutVertices;
        };
    }

    public int vertexCount(RenderLayer layer) {
        return switch (layer) {
            case OPAQUE -> opaqueVertices;
            case CUTOUT -> cutoutVertices;
            case TRANSLUCENT -> translucentVertices;
        };
    }

    void validate(MeshFormat format) {
        if (opaqueVertices + cutoutVertices + translucentVertices != format.vertexCount())
            throw new IllegalArgumentException("Layer ranges must cover the complete vertex buffer");
        format.topology().validateVertexCount(opaqueVertices);
        format.topology().validateVertexCount(cutoutVertices);
        format.topology().validateVertexCount(translucentVertices);
    }
}
