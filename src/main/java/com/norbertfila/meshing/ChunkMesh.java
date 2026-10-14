package com.norbertfila.meshing;

import java.util.Objects;

/**
 * Mesh result for one chunk split into independently replaceable parts. The solid part holds opaque and cutout
 * geometry, which depends only on block types; the water part holds water surfaces, which also depend on water
 * levels. A null part was not rebuilt and keeps its previous geometry.
 */
public record ChunkMesh(MeshData solid, MeshData water) {
    public static final int SOLID = 1;
    public static final int WATER = 2;
    public static final int ALL = SOLID | WATER;

    private static final MeshData EMPTY = MeshData.textured(new float[0]);

    public ChunkMesh {
        if (solid == null && water == null) throw new IllegalArgumentException("A chunk mesh needs at least one part");
    }

    /** A complete mesh from a mesher that keeps every layer in one buffer; any old water part is cleared. */
    public static ChunkMesh single(MeshData mesh) {
        return new ChunkMesh(Objects.requireNonNull(mesh), EMPTY);
    }

    public int parts() {
        return (solid == null ? 0 : SOLID) | (water == null ? 0 : WATER);
    }

    public int vertexCount() {
        return (solid == null ? 0 : solid.vertexCount()) + (water == null ? 0 : water.vertexCount());
    }

    public long bytes() {
        return bytes(solid) + bytes(water);
    }

    private static long bytes(MeshData mesh) {
        return mesh == null ? 0 : (long) mesh.vertexCount() * mesh.format().stride() * Float.BYTES;
    }

    static void checkParts(int parts) {
        if (parts == 0 || (parts & ~ALL) != 0) throw new IllegalArgumentException("Invalid mesh parts: " + parts);
    }
}
