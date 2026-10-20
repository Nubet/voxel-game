package com.norbertfila.light;

import com.norbertfila.world.BlockMutation;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;

/**
 * Block light from placed emitters in a box around the player, recomputed on the world thread after edits or when
 * the player nears the box edge. Recomputing is throttled because a full flood touches every lit cell.
 */
final class BlockLightField {
    static final int HALF_WIDTH = 64, HALF_HEIGHT = 48;
    // The player may drift this far from the center while every light within reach still fits inside the box.
    private static final int RECENTER_HORIZONTAL = HALF_WIDTH - 2 * LightLevels.MAX_LEVEL;
    private static final int RECENTER_VERTICAL = HALF_HEIGHT - 2 * LightLevels.MAX_LEVEL;
    private static final int MIN_UPDATES_BETWEEN_FLOODS = 6;
    private final LightSources sources;
    private final BlockReader reader;
    private BlockLightVolume volume = BlockLightVolume.EMPTY;
    private int centerX, centerY, centerZ;
    private boolean centered, dirty = true;
    private long updates, lastFlood = -MIN_UPDATES_BETWEEN_FLOODS;

    BlockLightField(World world) {
        sources = new LightSources(world);
        reader = new BlockReader(world);
    }

    BlockLightVolume update(BlockPos player) {
        updates++;
        if (!centered
                || Math.abs(player.x() - centerX) > RECENTER_HORIZONTAL
                || Math.abs(player.z() - centerZ) > RECENTER_HORIZONTAL
                || Math.abs(player.y() - centerY) > RECENTER_VERTICAL) {
            centerX = snap(player.x());
            centerY = snap(player.y());
            centerZ = snap(player.z());
            centered = true;
            dirty = true;
        }
        if (dirty && updates - lastFlood >= MIN_UPDATES_BETWEEN_FLOODS) {
            volume = compute();
            dirty = false;
            lastFlood = updates;
        }
        return volume;
    }

    void onBlockChanged(BlockMutation mutation) {
        if (dirty || !centered) return;
        var position = mutation.position();
        int previous = mutation.previous().orElse(0);
        boolean emissionChanged = BlockRegistry.DEFAULT.lightEmission((byte) previous)
                != BlockRegistry.DEFAULT.lightEmission(mutation.block());
        dirty = inside(position.x(), position.y(), position.z())
                && (emissionChanged || litAround(position.x(), position.y(), position.z()));
    }

    void onChunkChanged(ChunkPos chunk) {
        if (!centered) return;
        int x = chunk.x() * Chunk.SIZE, y = chunk.y() * Chunk.SIZE, z = chunk.z() * Chunk.SIZE;
        if (x + Chunk.SIZE > centerX - HALF_WIDTH
                && x < centerX + HALF_WIDTH
                && y + Chunk.SIZE > centerY - HALF_HEIGHT
                && y < centerY + HALF_HEIGHT
                && z + Chunk.SIZE > centerZ - HALF_WIDTH
                && z < centerZ + HALF_WIDTH) dirty = true;
    }

    private BlockLightVolume compute() {
        int originX = centerX - HALF_WIDTH, originY = centerY - HALF_HEIGHT, originZ = centerZ - HALF_WIDTH;
        var flood = new LightFlood(reader, originX, originY, originZ, 2 * HALF_WIDTH, 2 * HALF_HEIGHT, 2 * HALF_WIDTH);
        for (int cx = Math.floorDiv(originX, Chunk.SIZE);
                cx <= Math.floorDiv(centerX + HALF_WIDTH - 1, Chunk.SIZE);
                cx++)
            for (int cy = Math.floorDiv(originY, Chunk.SIZE);
                    cy <= Math.floorDiv(centerY + HALF_HEIGHT - 1, Chunk.SIZE);
                    cy++)
                for (int cz = Math.floorDiv(originZ, Chunk.SIZE);
                        cz <= Math.floorDiv(centerZ + HALF_WIDTH - 1, Chunk.SIZE);
                        cz++) {
                    int[] emitters = sources.emitters(new ChunkPos(cx, cy, cz));
                    if (emitters == null) continue;
                    for (int packed : emitters)
                        flood.seed(
                                cx * Chunk.SIZE + LightSources.localX(packed),
                                cy * Chunk.SIZE + LightSources.localY(packed),
                                cz * Chunk.SIZE + LightSources.localZ(packed),
                                LightSources.level(packed));
                }
        return flood.propagate();
    }

    private boolean inside(int x, int y, int z) {
        return Math.abs(x - centerX) <= HALF_WIDTH
                && Math.abs(y - centerY) <= HALF_HEIGHT
                && Math.abs(z - centerZ) <= HALF_WIDTH;
    }

    /** An edit only changes the field if it opens or closes a path next to a cell that already has light. */
    private boolean litAround(int x, int y, int z) {
        return volume.level(x, y, z) > 0
                || volume.level(x + 1, y, z) > 0
                || volume.level(x - 1, y, z) > 0
                || volume.level(x, y + 1, z) > 0
                || volume.level(x, y - 1, z) > 0
                || volume.level(x, y, z + 1) > 0
                || volume.level(x, y, z - 1) > 0;
    }

    private static int snap(int coordinate) {
        return Math.floorDiv(coordinate, Chunk.SIZE) * Chunk.SIZE;
    }
}
