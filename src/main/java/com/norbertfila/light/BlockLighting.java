package com.norbertfila.light;

import com.norbertfila.world.BlockMutation;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.Objects;

/** Visible block light around the player, owned by the world thread and published for rendering. */
public final class BlockLighting {
    private final World world;
    private final BlockLightField placed;
    private final HeldLight held;

    public BlockLighting(World world) {
        this.world = Objects.requireNonNull(world);
        placed = new BlockLightField(world);
        held = new HeldLight(world);
    }

    public void onBlockChanged(BlockMutation mutation) {
        placed.onBlockChanged(mutation);
        held.onBlockChanged(mutation);
    }

    public void onChunkChanged(ChunkPos chunk) {
        placed.onChunkChanged(chunk);
        held.onChunkChanged(chunk);
    }

    /** {@code heldEmission} is the light level of what the player carries, 0 for nothing that glows. */
    public BlockLightState update(BlockPos feet, BlockPos eye, int heldEmission) {
        world.assertOwner();
        return new BlockLightState(placed.update(feet), held.update(eye, heldEmission));
    }
}
