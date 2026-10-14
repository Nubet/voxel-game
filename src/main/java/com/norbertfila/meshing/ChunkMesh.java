package com.norbertfila.meshing;

/**
 * Mesh result for one chunk split into independently replaceable parts. The solid part holds opaque and cutout
 * quads, which depend only on block types; the water part holds water surfaces, which also depend on water levels.
 * A null part was not rebuilt and keeps its previous geometry.
 */
public record ChunkMesh(QuadMesh solid, QuadMesh water) {
    public static final int SOLID = 1;
    public static final int WATER = 2;
    public static final int ALL = SOLID | WATER;

    public ChunkMesh {
        if (solid == null && water == null) throw new IllegalArgumentException("A chunk mesh needs at least one part");
        if (solid != null && solid.format() != QuadMesh.Format.SOLID
                || water != null && water.format() != QuadMesh.Format.WATER)
            throw new IllegalArgumentException("Mesh part has the wrong quad format");
    }

    public int parts() {
        return (solid == null ? 0 : SOLID) | (water == null ? 0 : WATER);
    }

    public int quadCount() {
        return (solid == null ? 0 : solid.quadCount()) + (water == null ? 0 : water.quadCount());
    }

    public long bytes() {
        return (solid == null ? 0 : solid.bytes()) + (water == null ? 0 : water.bytes());
    }

    static void checkParts(int parts) {
        if (parts == 0 || (parts & ~ALL) != 0) throw new IllegalArgumentException("Invalid mesh parts: " + parts);
    }
}
