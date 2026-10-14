package com.norbertfila.interaction;

import com.norbertfila.player.PlayerController;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.PlantRules;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class BlockInteraction {
    private static final double REACH = 6;
    private final World world;
    private final WorldEdit edit;
    private final VoxelRaycaster raycaster = new VoxelRaycaster();
    private final Consumer<BlockPos> changed;

    public BlockInteraction(World world, int farTileSize) {
        this(world, farTileSize, ignored -> {});
    }

    public BlockInteraction(World world, int farTileSize, Consumer<BlockPos> changed) {
        this.world = Objects.requireNonNull(world);
        edit = new WorldEdit(world, farTileSize);
        this.changed = Objects.requireNonNull(changed);
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
                .cast(
                        world,
                        originX,
                        originY,
                        originZ,
                        directionX,
                        directionY,
                        directionZ,
                        REACH,
                        id -> canTarget(mode, id))
                .filter(hit -> canBreak(mode, hit.blockId()))
                .map(hit -> setBlock(hit.block(), BlockId.AIR));
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
        Optional<VoxelRaycaster.Hit> target = raycaster.cast(
                world, originX, originY, originZ, directionX, directionY, directionZ, REACH, id -> canTarget(mode, id));
        if (target.isEmpty()) return Optional.empty();
        VoxelRaycaster.Hit hit = target.get();
        VoxelRaycaster.Normal normal = hit.normal();
        boolean replacesTarget =
                BlockRegistry.DEFAULT.definition((byte) hit.blockId()).replaceable();
        if (!replacesTarget && normal.equals(VoxelRaycaster.Normal.ZERO)) return Optional.empty();
        long x = (long) hit.block().x() + normal.x();
        long y = (long) hit.block().y() + normal.y();
        long z = (long) hit.block().z() + normal.z();
        if (!replacesTarget
                && (x < Integer.MIN_VALUE
                        || x > Integer.MAX_VALUE
                        || y < Integer.MIN_VALUE
                        || y > Integer.MAX_VALUE
                        || z < Integer.MIN_VALUE
                        || z > Integer.MAX_VALUE)) return Optional.empty();
        BlockPos position = replacesTarget ? hit.block() : new BlockPos((int) x, (int) y, (int) z);
        if (!world.hasChunk(Coordinates.worldToChunk(position))) return Optional.empty();
        int previous = world.getBlock(position).orElseThrow();
        if (!BlockRegistry.DEFAULT.definition((byte) previous).replaceable()) return Optional.empty();
        if (PlantRules.isPlant(blockId) && !PlantRules.canPlace(world, position)) return Optional.empty();
        if (BlockRegistry.DEFAULT.isCollidable(blockId)
                && playerBody.intersects(new Aabb(
                        position.x(),
                        position.y(),
                        position.z(),
                        position.x() + 1.0,
                        position.y() + 1.0,
                        position.z() + 1.0))) return Optional.empty();
        return Optional.of(setBlock(position, blockId));
    }

    private BlockChangeResult setBlock(BlockPos position, byte value) {
        BlockChangeResult result = edit.setBlock(position, value);
        if (result.changed()) changed.accept(position);
        return result;
    }

    private static boolean canTarget(PlayerController.Mode mode, int id) {
        return mode == PlayerController.Mode.CREATIVE
                || BlockRegistry.DEFAULT.definition((byte) id).targetable();
    }

    private static boolean canBreak(PlayerController.Mode mode, int id) {
        return mode == PlayerController.Mode.CREATIVE
                || BlockRegistry.DEFAULT.definition((byte) id).breakable();
    }
}
