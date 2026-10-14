package com.norbertfila.meshing;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mesher input: a center chunk plus the snapshots of its 26 neighbors that were resident when the task was created.
 * Coordinates are local to the center chunk and may extend one chunk beyond it in every direction. A missing
 * neighbor reads as air for faces and as {@link WaterSurface#UNKNOWN} for water surfaces.
 */
public final class BlockNeighborhood {
    public enum Side {
        POSITIVE_X(1, 0, 0),
        NEGATIVE_X(-1, 0, 0),
        POSITIVE_Y(0, 1, 0),
        NEGATIVE_Y(0, -1, 0),
        POSITIVE_Z(0, 0, 1),
        NEGATIVE_Z(0, 0, -1);
        private final int dx, dy, dz;

        Side(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    private static final int CENTER = slot(0, 0, 0);
    private final ChunkSnapshot[] chunks;
    private final long revision;

    /** {@code chunks} is indexed by {@link #slot}; the array is copied. */
    public BlockNeighborhood(ChunkSnapshot[] chunks) {
        if (chunks.length != 27) throw new IllegalArgumentException("Neighborhood needs 27 slots");
        this.chunks = chunks.clone();
        Objects.requireNonNull(this.chunks[CENTER]);
        long combined = this.chunks[CENTER].revision();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    var snapshot = this.chunks[slot(dx, dy, dz)];
                    combined = 31 * combined + (snapshot == null ? 0 : snapshot.revision() + 1);
                }
        revision = combined;
    }

    /** {@code halo} maps offsets in chunks relative to the center to neighbor snapshots. */
    public BlockNeighborhood(ChunkSnapshot center, Map<ChunkPos, ChunkSnapshot> halo) {
        this(slots(center, halo));
    }

    public BlockNeighborhood(
            ChunkSnapshot center,
            ChunkSnapshot positiveX,
            ChunkSnapshot negativeX,
            ChunkSnapshot positiveY,
            ChunkSnapshot negativeY,
            ChunkSnapshot positiveZ,
            ChunkSnapshot negativeZ) {
        this(faceSlots(center, positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ));
    }

    public static BlockNeighborhood centerOnly(ChunkSnapshot center) {
        return new BlockNeighborhood(center, null, null, null, null, null, null);
    }

    public static int slot(int dx, int dy, int dz) {
        return (dx + 1) * 9 + (dy + 1) * 3 + dz + 1;
    }

    private static ChunkSnapshot[] slots(ChunkSnapshot center, Map<ChunkPos, ChunkSnapshot> halo) {
        ChunkSnapshot[] chunks = new ChunkSnapshot[27];
        chunks[CENTER] = center;
        halo.forEach((offset, snapshot) -> {
            if (Math.abs(offset.x()) > 1 || Math.abs(offset.y()) > 1 || Math.abs(offset.z()) > 1)
                throw new IllegalArgumentException("Halo offset outside the neighborhood: " + offset);
            if (offset.x() != 0 || offset.y() != 0 || offset.z() != 0)
                chunks[slot(offset.x(), offset.y(), offset.z())] = snapshot;
        });
        return chunks;
    }

    private static ChunkSnapshot[] faceSlots(ChunkSnapshot center, ChunkSnapshot... faces) {
        ChunkSnapshot[] chunks = new ChunkSnapshot[27];
        chunks[CENTER] = center;
        for (Side side : Side.values()) chunks[slot(side.dx, side.dy, side.dz)] = faces[side.ordinal()];
        return chunks;
    }

    /** The snapshot containing local position {@code (x, y, z)}, or null when that chunk was not resident. */
    public ChunkSnapshot chunkAt(int x, int y, int z) {
        int dx = Math.floorDiv(x, Chunk.SIZE), dy = Math.floorDiv(y, Chunk.SIZE), dz = Math.floorDiv(z, Chunk.SIZE);
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) return null;
        return chunks[slot(dx, dy, dz)];
    }

    public Optional<ChunkSnapshot> sampleChunk(int x, int y, int z) {
        return Optional.ofNullable(chunkAt(x, y, z));
    }

    public byte block(int x, int y, int z) {
        ChunkSnapshot chunk = chunkAt(x, y, z);
        return chunk == null
                ? BlockId.AIR
                : chunk.get(Math.floorMod(x, Chunk.SIZE), Math.floorMod(y, Chunk.SIZE), Math.floorMod(z, Chunk.SIZE));
    }

    public int waterSample(int x, int y, int z) {
        ChunkSnapshot snapshot = chunkAt(x, y, z);
        if (snapshot == null) return WaterSurface.UNKNOWN;
        int lx = Math.floorMod(x, Chunk.SIZE), ly = Math.floorMod(y, Chunk.SIZE), lz = Math.floorMod(z, Chunk.SIZE);
        byte block = snapshot.get(lx, ly, lz);
        if (block == BlockId.WATER) return snapshot.waterState(lx, ly, lz);
        return BlockRegistry.DEFAULT.occludesFullFace(block) ? WaterSurface.SOLID : WaterSurface.EMPTY;
    }

    public ChunkSnapshot center() {
        return chunks[CENTER];
    }

    public Optional<ChunkSnapshot> neighbor(Side side) {
        return Optional.ofNullable(chunks[slot(side.dx, side.dy, side.dz)]);
    }

    public long revision() {
        return revision;
    }
}
