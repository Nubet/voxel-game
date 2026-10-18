package com.norbertfila.lod;

import com.norbertfila.meshing.IntList;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;

final class FarVolumeMesher {
    static boolean handles(FarTerrainTile tile, int x, int z) {
        return tile.volumeAt(x, z)
                || tile.volumeAt(x - 1, z)
                || tile.volumeAt(x + 1, z)
                || tile.volumeAt(x, z - 1)
                || tile.volumeAt(x, z + 1);
    }

    static void mesh(FarTerrainTile tile, IntList out, boolean water) {
        if (tile.volumeMinY() == Integer.MAX_VALUE) return;
        int step = tile.sampleStep();
        for (int z = 0; z < tile.cells(); z++)
            for (int x = 0; x < tile.cells(); x++) {
                if (!handles(tile, x, z)) continue;
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                int top = Math.max(tile.heightAt(x, z), tile.waterTopAt(x, z));
                for (int y = tile.volumeMinY(); y <= top; y++) {
                    byte material = tile.volumeMaterialAt(x, z, y);
                    if (!present(material, water)) continue;
                    boolean fluid = BlockId.isFluid(material);
                    int wx = x * step, wz = z * step;
                    for (int face = 0; face < 6; face++) {
                        if (material == BlockId.SNOW_LAYER && face != 2) continue;
                        int nx = x + (face == 0 ? 1 : face == 1 ? -1 : 0);
                        int nz = z + (face == 4 ? 1 : face == 5 ? -1 : 0);
                        int ny = y + (face == 2 ? 1 : face == 3 ? -1 : 0);
                        byte neighbor = tile.volumeMaterialAt(nx, nz, ny);
                        if (BlockRegistry.DEFAULT.isCollidable(neighbor) || fluid && neighbor == material) continue;
                        int texture = BlockRegistry.DEFAULT.definition(material).textureLayer(face);
                        if (!water) texture |= tile.tintAt(x, z) << 8;
                        switch (face) {
                            case 0 -> FarTileMesher.add(out, wx + step, y, wz, face, fluid, step, 1, texture);
                            case 1 -> FarTileMesher.add(out, wx, y, wz + step, face, fluid, step, 1, texture);
                            case 2 ->
                                FarTileMesher.add(
                                        out,
                                        wx,
                                        y + 1,
                                        wz + step,
                                        face,
                                        fluid,
                                        step,
                                        step,
                                        texture,
                                        material == BlockId.SNOW_LAYER);
                            case 3 -> FarTileMesher.add(out, wx, y, wz, face, fluid, step, step, texture);
                            case 4 -> FarTileMesher.add(out, wx + step, y, wz + step, face, fluid, step, 1, texture);
                            case 5 -> FarTileMesher.add(out, wx, y, wz, face, fluid, step, 1, texture);
                            default -> throw new IllegalStateException();
                        }
                    }
                }
            }
    }

    private static boolean present(byte block, boolean water) {
        return water
                ? block == BlockId.WATER || block == BlockId.ICE
                : block != BlockId.ICE && (BlockRegistry.DEFAULT.isCollidable(block) || block == BlockId.LAVA);
    }
}
