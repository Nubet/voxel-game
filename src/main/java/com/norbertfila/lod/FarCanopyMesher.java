package com.norbertfila.lod;

import com.norbertfila.generation.*;
import com.norbertfila.meshing.IntList;
import com.norbertfila.world.BlockRegistry;

final class FarCanopyMesher {
    static int[] mesh(TerrainSource source, int ox, int oz, int sampleStep, AreaEdits edits) {
        var out = new IntList();
        for (var canopy : source.canopies(ox, oz, 64)) {
            int step = sampleStep < 8 ? 2 : 4;
            for (int c = 0; c < edits.columnCount(); c++)
                if (Math.abs((long) edits.columnX(c) - canopy.x()) <= canopy.radius()
                        && Math.abs((long) edits.columnZ(c) - canopy.z()) <= canopy.radius()) {
                    step = 1;
                    break;
                }
            int minX = Math.max(0, Math.floorDiv(canopy.x() - canopy.radius() - ox, step) * step);
            int minZ = Math.max(0, Math.floorDiv(canopy.z() - canopy.radius() - oz, step) * step);
            int maxX = Math.min(64, canopy.x() + canopy.radius() - ox + 1),
                    maxZ = Math.min(64, canopy.z() + canopy.radius() - oz + 1);
            for (int z = minZ; z < maxZ; z += step)
                for (int x = minX; x < maxX; x += step) {
                    int sx = ox + x + step / 2, sz = oz + z + step / 2;
                    int texture =
                            BlockRegistry.DEFAULT.definition(canopy.leaves()).sideTexture()
                                    | source.tintIndex(sx, sz) << 8;
                    for (int y = canopy.y() - canopy.height(); y <= canopy.y() + canopy.height(); y++) {
                        if (!present(canopy, sx, y, sz, edits)) continue;
                        if (!present(canopy, sx, y + 1, sz, edits))
                            FarTileMesher.add(out, x, y + 1, z + step, 2, false, step, step, texture, false);
                        if (!present(canopy, sx, y - 1, sz, edits))
                            FarTileMesher.add(out, x, y, z, 3, false, step, step, texture, false);
                        if (!present(canopy, sx + step, y, sz, edits))
                            FarTileMesher.add(out, x + step, y, z, 0, false, step, 1, texture, false);
                        if (!present(canopy, sx - step, y, sz, edits))
                            FarTileMesher.add(out, x, y, z + step, 1, false, step, 1, texture, false);
                        if (!present(canopy, sx, y, sz + step, edits))
                            FarTileMesher.add(out, x + step, y, z + step, 4, false, step, 1, texture, false);
                        if (!present(canopy, sx, y, sz - step, edits))
                            FarTileMesher.add(out, x, y, z, 5, false, step, 1, texture, false);
                    }
                }
        }
        return out.toArray();
    }

    private static boolean present(Canopy canopy, int x, int y, int z, AreaEdits edits) {
        if (!canopy.contains(x, y, z)) return false;
        int column = edits.find(x, z);
        if (column >= 0)
            for (int i = 0; i < edits.changeCount(column); i++) {
                if (edits.changeY(column, i) < y) break;
                if (edits.changeY(column, i) == y) return false;
            }
        return true;
    }
}
