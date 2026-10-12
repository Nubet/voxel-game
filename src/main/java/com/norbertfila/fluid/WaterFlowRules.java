package com.norbertfila.fluid;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import java.util.Set;

final class WaterFlowRules {
    static final int BLOCKED = -2;
    static final int UNAVAILABLE = -3;
    static final int[] DX = {-1, 1, 0, 0};
    static final int[] DZ = {0, 0, -1, 1};

    private WaterFlowRules() {}

    static int nextState(World world, BlockPos position, Set<ChunkPos> missing) {
        int current = read(world, position, missing);
        if (current == BLOCKED || current == UNAVAILABLE || current == WaterState.SOURCE) return current;
        int above = read(world, offset(position, 0, 1, 0), missing);
        if (above == UNAVAILABLE) return UNAVAILABLE;
        if (above >= WaterState.SOURCE) return WaterState.FALLING;

        int best = WaterState.MAX_DISTANCE + 1;
        boolean unknown = false;
        for (int direction = 0; direction < DX.length; direction++) {
            BlockPos neighbor = offset(position, DX[direction], 0, DZ[direction]);
            int state = read(world, neighbor, missing);
            if (state == UNAVAILABLE) unknown = true;
            if (state < WaterState.SOURCE) continue;
            int distance = WaterState.distance((byte) state) + 1;
            if (distance > WaterState.MAX_DISTANCE) continue;
            int below = read(world, offset(neighbor, 0, -1, 0), missing);
            if (below == UNAVAILABLE) unknown = true;
            // A donor that can continue downward must not feed a horizontal sheet.
            if (below == BLOCKED || below == WaterState.SOURCE) best = Math.min(best, distance);
        }
        if (unknown) return UNAVAILABLE;
        return best <= WaterState.MAX_DISTANCE ? best : WaterState.EMPTY;
    }

    private static int read(World world, BlockPos position, Set<ChunkPos> missing) {
        if (position == null) return BLOCKED;
        ChunkPos chunk = Coordinates.worldToChunk(position);
        if (!world.hasChunk(chunk)) {
            missing.add(chunk);
            return UNAVAILABLE;
        }
        int block = world.getBlock(position).orElseThrow();
        if (block == BlockId.AIR) return WaterState.EMPTY;
        if (block != BlockId.WATER) return BLOCKED;
        return world.getWaterState(position).orElseThrow();
    }

    static BlockPos offset(BlockPos position, int dx, int dy, int dz) {
        if (position == null) return null;
        long x = (long) position.x() + dx, y = (long) position.y() + dy, z = (long) position.z() + dz;
        if (x < Integer.MIN_VALUE
                || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE
                || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE
                || z > Integer.MAX_VALUE) return null;
        return new BlockPos((int) x, (int) y, (int) z);
    }
}
