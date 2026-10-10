package com.norbertfila.world;

/** Deterministic demo heights. This is NOT final terrain generation */
public final class HillsGenerator implements WorldGenerator {
    @Override
    public Chunk generate(int cx, int cy, int cz) {
        Chunk chunk = new Chunk();
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int wx = cx * Chunk.SIZE + x, wz = cz * Chunk.SIZE + z;
                int surface = 9 + (int) (5 * Math.sin(wx * 0.065) * Math.cos(wz * 0.071));
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int wy = cy * Chunk.SIZE + y;
                    byte id = wy > surface
                            ? BlockId.AIR
                            : wy == surface ? BlockId.GRASS : wy >= surface - 3 ? BlockId.DIRT : BlockId.STONE;
                    chunk.set(x, y, z, id);
                }
            }
        return chunk;
    }
}
