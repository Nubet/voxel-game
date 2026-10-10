package com.norbertfila.interaction;

import com.norbertfila.player.PlayerController;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.util.Objects;
import java.util.Optional;

public final class BlockInteraction {
    private static final double REACH = 6;
    private final World world;
    private final WorldEdit edit;
    private final VoxelRaycaster raycaster = new VoxelRaycaster();

    public BlockInteraction(World world, int farTileSize) {
        this.world = Objects.requireNonNull(world);
        edit = new WorldEdit(world, farTileSize);
    }

    public Optional<BlockChangeResult> remove(
            PlayerController.Mode mode,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ) {
        if (Objects.requireNonNull(mode) == PlayerController.Mode.SPECTATOR) return Optional.empty();
        return raycaster
                .cast(world, originX, originY, originZ, directionX, directionY, directionZ, REACH)
                .map(hit -> edit.setBlock(hit.block(), BlockId.AIR));
    }

    public Optional<BlockChangeResult> place(
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            byte blockId,
            PlayerController.Mode mode,
            Aabb playerBody) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(playerBody);
        if (!BlockId.isValid(blockId))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(blockId));
        if (blockId == BlockId.AIR || mode == PlayerController.Mode.SPECTATOR) return Optional.empty();
        Optional<VoxelRaycaster.Hit> target =
                raycaster.cast(world, originX, originY, originZ, directionX, directionY, directionZ, REACH);
        if (target.isEmpty()) return Optional.empty();
        VoxelRaycaster.Hit hit = target.get();
        VoxelRaycaster.Normal normal = hit.normal();
        if (normal.equals(VoxelRaycaster.Normal.ZERO)) return Optional.empty();
        BlockPos position = new BlockPos(
                hit.block().x() + normal.x(),
                hit.block().y() + normal.y(),
                hit.block().z() + normal.z());
        if (!world.hasChunk(Coordinates.worldToChunk(position))
                || world.getBlock(position).orElseThrow() != BlockId.AIR) return Optional.empty();
        if (playerBody.intersects(new Aabb(
                position.x(), position.y(), position.z(), position.x() + 1.0, position.y() + 1.0, position.z() + 1.0)))
            return Optional.empty();
        return Optional.of(edit.setBlock(position, blockId));
    }
}
