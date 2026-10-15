package com.norbertfila.lod;

import com.norbertfila.meshing.QuadMesh;
import java.util.Objects;

/** Immutable mesh of one far tile with the vertical extent of its quads, for culling. */
public record FarTileMesh(int tileX, int tileZ, int sampleStep, long editRevision, QuadMesh mesh, int minY, int maxY) {
    public FarTileMesh {
        Objects.requireNonNull(mesh);
        if (mesh.format() != QuadMesh.Format.FAR) throw new IllegalArgumentException("Far tiles use far records");
        if (minY > maxY) throw new IllegalArgumentException("Invalid vertical extent");
    }

    static FarTileMesh of(FarTerrainTile tile, QuadMesh mesh) {
        int[] records = mesh.records();
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int index = 0; index < records.length; index += 3) {
            long y = records[index + 1];
            boolean top = (records[index] >>> 14 & 7) == FarTileMesher.FACE_TOP;
            long extent = top ? 1 : (records[index + 2] & 0xFFFF) + 1L;
            min = Math.min(min, y - 1);
            max = Math.max(max, y + extent);
        }
        if (records.length == 0) min = max = 0;
        return new FarTileMesh(
                tile.tileX(),
                tile.tileZ(),
                tile.sampleStep(),
                tile.editRevision(),
                mesh,
                (int) Math.max(Integer.MIN_VALUE, min),
                (int) Math.min(Integer.MAX_VALUE, max));
    }

    public FarTilePos position() {
        return new FarTilePos(tileX, tileZ);
    }
}
