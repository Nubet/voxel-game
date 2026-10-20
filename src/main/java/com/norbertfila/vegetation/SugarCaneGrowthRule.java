package com.norbertfila.vegetation;

import com.norbertfila.world.*;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SplittableRandom;

public final class SugarCaneGrowthRule implements GrowthRule {
    private static final double LOG_MISS = Math.log1p(-3.0 / 4096);

    @Override
    public byte blockId() {
        return BlockId.REED;
    }

    @Override
    public long delay(long seed, BlockPos root, long startedAt, long cycle) {
        long key = seed
                ^ mix(root.x())
                ^ Long.rotateLeft(mix(root.z()), 21)
                ^ Long.rotateLeft(mix(root.y()), 42)
                ^ mix(startedAt)
                ^ mix(cycle + 0x632BE59BD9B4E019L);
        var random = new SplittableRandom(key);
        long trials = 0;
        // Sixteen geometric waits approximate Minecraft's random ticks; three engine ticks equal one 20 Hz tick.
        for (int i = 0; i < 16; i++) trials += 1 + (long) (Math.log1p(-random.nextDouble()) / LOG_MISS);
        return Math.multiplyExact(trials, 3);
    }

    @Override
    public GrowthSite inspect(World world, BlockPos root) {
        var dependencies = new HashSet<ChunkPos>();
        var block = read(world, root, dependencies);
        if (block.isEmpty()) return site(GrowthSite.Status.UNAVAILABLE, root, dependencies);
        if (block.getAsInt() != BlockId.REED) return site(GrowthSite.Status.INVALID, root, dependencies);
        var ground = new BlockPos(root.x(), root.y() - 1, root.z());
        var below = read(world, ground, dependencies);
        if (below.isEmpty()) return site(GrowthSite.Status.UNAVAILABLE, root, dependencies);
        if (below.getAsInt() == BlockId.REED || !PlantRules.supports(BlockId.REED, (byte) below.getAsInt(), true))
            return site(GrowthSite.Status.INVALID, root, dependencies);
        boolean water = false, missing = false;
        for (int[] side : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            long x = (long) ground.x() + side[0], z = (long) ground.z() + side[1];
            if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
                continue;
            var neighbor = read(world, new BlockPos((int) x, ground.y(), (int) z), dependencies);
            missing |= neighbor.isEmpty();
            water |= neighbor.orElse(BlockId.AIR) == BlockId.WATER;
        }
        if (missing) return site(GrowthSite.Status.UNAVAILABLE, root, dependencies);
        if (!water) return site(GrowthSite.Status.INVALID, root, dependencies);
        for (int height = 1; height < 3; height++) {
            var target = new BlockPos(root.x(), root.y() + height, root.z());
            if (!WorldBounds.containsY(target.y())) return site(GrowthSite.Status.BLOCKED, target, dependencies);
            var above = read(world, target, dependencies);
            if (above.isEmpty()) return site(GrowthSite.Status.UNAVAILABLE, target, dependencies);
            if (above.getAsInt() != BlockId.REED)
                return site(
                        above.getAsInt() == BlockId.AIR ? GrowthSite.Status.READY : GrowthSite.Status.BLOCKED,
                        target,
                        dependencies);
        }
        return site(GrowthSite.Status.MATURE, root, dependencies);
    }

    private static OptionalInt read(World world, BlockPos position, Set<ChunkPos> dependencies) {
        var chunk = Coordinates.worldToChunk(position);
        dependencies.add(chunk);
        return world.hasChunk(chunk) ? world.getBlock(position) : OptionalInt.empty();
    }

    private static GrowthSite site(GrowthSite.Status status, BlockPos target, Set<ChunkPos> dependencies) {
        return new GrowthSite(status, target, dependencies);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
