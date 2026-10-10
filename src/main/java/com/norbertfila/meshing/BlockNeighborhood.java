package com.norbertfila.meshing;

import com.norbertfila.world.ChunkSnapshot;
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

    public BlockNeighborhood(
            ChunkSnapshot center,
            ChunkSnapshot positiveX,
            ChunkSnapshot negativeX,
            ChunkSnapshot positiveY,
            ChunkSnapshot negativeY,
            ChunkSnapshot positiveZ,
            ChunkSnapshot negativeZ) {
        this.center = center;
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
