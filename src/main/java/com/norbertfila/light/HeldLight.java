package com.norbertfila.light;

import com.norbertfila.world.BlockMutation;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;

/** Light carried by the player, such as a held torch, flooded from the eye position like a placed emitter. */
final class HeldLight {
    private static final int REACH = LightLevels.MAX_LEVEL - 1;
    private final BlockReader reader;
    private BlockLightVolume volume = BlockLightVolume.EMPTY;
    private BlockPos source;
    private int level;
    private boolean dirty;

    HeldLight(World world) {
        reader = new BlockReader(world);
    }

    BlockLightVolume update(BlockPos eye, int emission) {
        if (emission < 0 || emission > LightLevels.MAX_LEVEL) throw new IllegalArgumentException("Invalid emission");
        if (emission == 0) {
            volume = BlockLightVolume.EMPTY;
            source = null;
            level = 0;
            return volume;
        }
        if (!dirty && emission == level && eye.equals(source)) return volume;
        var flood = new LightFlood(
                reader, eye.x() - REACH, eye.y() - REACH, eye.z() - REACH, 2 * REACH + 1, 2 * REACH + 1, 2 * REACH + 1);
        flood.seed(eye.x(), eye.y(), eye.z(), emission);
        volume = flood.propagate();
        source = eye;
        level = emission;
        dirty = false;
        return volume;
    }

    void onBlockChanged(BlockMutation mutation) {
        if (source == null) return;
        var position = mutation.position();
        dirty |= Math.abs(position.x() - source.x()) <= REACH
                && Math.abs(position.y() - source.y()) <= REACH
                && Math.abs(position.z() - source.z()) <= REACH;
    }

    void onChunkChanged(ChunkPos chunk) {
        dirty |= source != null;
    }
}
