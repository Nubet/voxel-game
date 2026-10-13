package com.norbertfila.meshing;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkSnapshot;
import java.util.Map;
import java.util.Optional;

public final class BlockNeighborhood {
    public enum Side {
        POSITIVE_X,
        NEGATIVE_X,
        POSITIVE_Y,
        NEGATIVE_Y,
        POSITIVE_Z,
        NEGATIVE_Z
    }

    private final ChunkSnapshot center;
    private final ChunkSnapshot[] neighbors;
    private final long revision;
    private final Map<ChunkPos, ChunkSnapshot> halo;

    public BlockNeighborhood(ChunkSnapshot center, Map<ChunkPos, ChunkSnapshot> halo) {
        this.center = center;
        neighbors = new ChunkSnapshot[] {
            halo.get(new ChunkPos(1, 0, 0)),
            halo.get(new ChunkPos(-1, 0, 0)),
            halo.get(new ChunkPos(0, 1, 0)),
            halo.get(new ChunkPos(0, -1, 0)),
            halo.get(new ChunkPos(0, 0, 1)),
            halo.get(new ChunkPos(0, 0, -1))
        };
        this.halo = Map.copyOf(halo);
        long combined = center.revision();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    var snapshot = halo.get(new ChunkPos(dx, dy, dz));
                    combined = 31 * combined + (snapshot == null ? 0 : snapshot.revision() + 1);
                }
        revision = combined;
    }

    public Optional<ChunkSnapshot> sampleChunk(int x, int y, int z) {
        int dx = Math.floorDiv(x, Chunk.SIZE), dy = Math.floorDiv(y, Chunk.SIZE), dz = Math.floorDiv(z, Chunk.SIZE);
        if (dx == 0 && dy == 0 && dz == 0) return Optional.of(center);
        ChunkSnapshot snapshot = halo.get(new ChunkPos(dx, dy, dz));
        if (snapshot == null) {
            if (dx == 1 && dy == 0 && dz == 0) snapshot = neighbors[0];
            else if (dx == -1 && dy == 0 && dz == 0) snapshot = neighbors[1];
            else if (dy == 1 && dx == 0 && dz == 0) snapshot = neighbors[2];
            else if (dy == -1 && dx == 0 && dz == 0) snapshot = neighbors[3];
            else if (dz == 1 && dx == 0 && dy == 0) snapshot = neighbors[4];
            else if (dz == -1 && dx == 0 && dy == 0) snapshot = neighbors[5];
        }
        return Optional.ofNullable(snapshot);
    }

    public byte block(int x, int y, int z) {
        return sampleChunk(x, y, z)
                .map(c ->
                        c.get(Math.floorMod(x, Chunk.SIZE), Math.floorMod(y, Chunk.SIZE), Math.floorMod(z, Chunk.SIZE)))
                .orElse(BlockId.AIR);
    }

    public int waterSample(int x, int y, int z) {
        var snapshot = sampleChunk(x, y, z).orElse(null);
        if (snapshot == null) return WaterSurface.UNKNOWN;
        int lx = Math.floorMod(x, Chunk.SIZE), ly = Math.floorMod(y, Chunk.SIZE), lz = Math.floorMod(z, Chunk.SIZE);
        byte block = snapshot.get(lx, ly, lz);
        if (block == BlockId.WATER) return snapshot.waterState(lx, ly, lz);
        return BlockRegistry.DEFAULT.occludesFullFace(block) ? WaterSurface.SOLID : WaterSurface.EMPTY;
    }

    public BlockNeighborhood(
            ChunkSnapshot center,
            ChunkSnapshot positiveX,
            ChunkSnapshot negativeX,
            ChunkSnapshot positiveY,
            ChunkSnapshot negativeY,
            ChunkSnapshot positiveZ,
            ChunkSnapshot negativeZ) {
        this.center = center;
        halo = Map.of();
        neighbors = new ChunkSnapshot[] {positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ};
        long combinedRevision = center.revision();
        for (ChunkSnapshot neighbor : neighbors) {
            combinedRevision = 31 * combinedRevision + (neighbor == null ? 0 : neighbor.revision() + 1);
        }
        revision = combinedRevision;
    }

    public static BlockNeighborhood centerOnly(ChunkSnapshot center) {
        return new BlockNeighborhood(center, null, null, null, null, null, null);
    }

    public ChunkSnapshot center() {
        return center;
    }

    public Optional<ChunkSnapshot> neighbor(Side side) {
        return Optional.ofNullable(neighbors[side.ordinal()]);
    }

    public long revision() {
        return revision;
    }
}
