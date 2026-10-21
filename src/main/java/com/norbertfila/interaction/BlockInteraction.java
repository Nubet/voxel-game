package com.norbertfila.interaction;

import com.norbertfila.items.BlockDropResolver;
import com.norbertfila.items.ItemStack;
import com.norbertfila.items.ItemSystem;
import com.norbertfila.items.ToolContext;
import com.norbertfila.player.PlayerController;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.PlantRules;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class BlockInteraction {
    private final World world;
    private final WorldEdit edit;
    private final BlockTargeting targeting;
    private final Consumer<BlockPos> changed;
    private final BlockDropResolver drops = new BlockDropResolver();

    public BlockInteraction(World world, int farTileSize) {
        this(world, farTileSize, ignored -> {});
    }

    public BlockInteraction(World world, int farTileSize, Consumer<BlockPos> changed) {
        this.world = Objects.requireNonNull(world);
        targeting = new BlockTargeting(world);
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
        return remove(mode, originX, originY, originZ, directionX, directionY, directionZ, null);
    }

    public Optional<BlockChangeResult> remove(
            PlayerController.Mode mode,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            ItemSystem items) {
        return remove(mode, originX, originY, originZ, directionX, directionY, directionZ, items, ToolContext.HAND);
    }

    public Optional<BlockChangeResult> remove(
            PlayerController.Mode mode,
            double originX,
            double originY,
            double originZ,
            double directionX,
            double directionY,
            double directionZ,
            ItemSystem items,
            ToolContext tool) {
        Objects.requireNonNull(tool);
        return targeting
                .find(mode, originX, originY, originZ, directionX, directionY, directionZ)
                .filter(hit -> canBreak(mode, hit.blockId()))
                .flatMap(hit -> {
                    var stacks = mode == PlayerController.Mode.SURVIVAL
                            ? drops.getDrops(hit.blockId(), tool)
                            : List.<ItemStack>of();
                    if (items != null && !items.canSpawn(hit.block(), stacks)) return Optional.empty();
                    var result = setBlock(hit.block(), BlockId.AIR);
                    if (result.changed() && items != null) items.spawn(hit.block(), stacks);
                    return Optional.of(result);
                });
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
                targeting.find(mode, originX, originY, originZ, directionX, directionY, directionZ);
        if (target.isEmpty()) return Optional.empty();
        VoxelRaycaster.Hit hit = target.get();
        VoxelRaycaster.Normal normal = hit.normal();
        if (PlantRules.attached(blockId)) {
            if (normal.y() != 0 || normal.equals(VoxelRaycaster.Normal.ZERO)) return Optional.empty();
            int side = normal.x() < 0 ? 0 : normal.x() > 0 ? 1 : normal.z() < 0 ? 2 : 3;
            int base = Byte.toUnsignedInt(blockId) <= Byte.toUnsignedInt(BlockId.VINE_NORTH)
                    ? Byte.toUnsignedInt(BlockId.VINE_EAST)
                    : Byte.toUnsignedInt(BlockId.COCOA_EAST);
            blockId = (byte) (base + side);
        }
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
        if (PlantRules.isPlant(blockId) && !PlantRules.canPlace(world, position, blockId)) return Optional.empty();
        if (BlockRegistry.DEFAULT.isCollidable(blockId)
                && playerBody.intersects(new Aabb(
                        position.x(),
                        position.y(),
                        position.z(),
                        position.x() + 1.0,
                        position.y()
                                + BlockRegistry.DEFAULT
                                        .definition(blockId)
                                        .model()
                                        .maxY(),
                        position.z() + 1.0))) return Optional.empty();
        return Optional.of(setBlock(position, blockId));
    }

    /** Sets every loaded block in the inclusive box; returns the chunks whose meshes became dirty. */
    public List<ChunkPos> fill(BlockPos min, BlockPos max, byte blockId) {
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        if (!BlockId.isValid(blockId))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(blockId));
        Set<ChunkPos> dirty = new LinkedHashSet<>();
        for (int y = min.y(); y <= max.y(); y++)
            for (int z = min.z(); z <= max.z(); z++)
                for (int x = min.x(); x <= max.x(); x++) {
                    var result = setBlock(new BlockPos(x, y, z), blockId);
                    if (result.changed()) dirty.addAll(result.dirtyChunks());
                }
        return List.copyOf(dirty);
    }

    private BlockChangeResult setBlock(BlockPos position, byte value) {
        BlockChangeResult result = edit.setBlock(position, value);
        if (result.changed()) changed.accept(position);
        return result;
    }

    private static boolean canBreak(PlayerController.Mode mode, int id) {
        return mode == PlayerController.Mode.CREATIVE
                || BlockRegistry.DEFAULT.definition((byte) id).breakable();
    }
}
