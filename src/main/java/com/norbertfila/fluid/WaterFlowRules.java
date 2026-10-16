package com.norbertfila.fluid;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.PlantRules;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldBounds;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class WaterFlowRules {
    static final int BLOCKED = -2;
    static final int UNAVAILABLE = -3;
    static final int[] DX = {-1, 1, 0, 0};
    static final int[] DZ = {0, 0, -1, 1};

    private WaterFlowRules() {}

    static int nextState(World world, BlockPos position, Set<ChunkPos> missing) {
        return nextState(world, position, missing, false);
    }

    static int nextState(World world, BlockPos position, Set<ChunkPos> missing, boolean extended) {
        int current = read(world, position, missing);
        if (current == BLOCKED || current == UNAVAILABLE || current == WaterState.SOURCE) return current;
        if (extended && current >= WaterState.SOURCE) {
            int sources = 0;
            for (int direction = 0; direction < DX.length; direction++)
                if (read(world, offset(position, DX[direction], 0, DZ[direction]), missing) == WaterState.SOURCE)
                    sources++;
            if (sources >= 2) {
                int support = read(world, offset(position, 0, -1, 0), missing);
                if (support == BLOCKED || support == WaterState.SOURCE) return WaterState.SOURCE;
            }
            if (!missing.isEmpty()) return UNAVAILABLE;
        }
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
            if (below == BLOCKED || below == WaterState.SOURCE) {
                if (!extended || feedsDirection(world, neighbor, direction ^ 1, missing))
                    best = Math.min(best, distance);
            }
        }
        if (unknown || !missing.isEmpty()) return UNAVAILABLE;
        return best <= WaterState.MAX_DISTANCE ? best : WaterState.EMPTY;
    }

    private static boolean feedsDirection(World world, BlockPos donor, int targetDirection, Set<ChunkPos> missing) {
        int[] scores = new int[DX.length];
        Map<BlockPos, Integer> samples = new HashMap<>();
        int best = Integer.MAX_VALUE;
        for (int direction = 0; direction < DX.length; direction++) {
            BlockPos next = offset(donor, DX[direction], 0, DZ[direction]);
            scores[direction] = dropDistance(world, next, donor, 1, missing, samples);
            best = Math.min(best, scores[direction]);
        }
        return scores[targetDirection] == best;
    }

    private static int dropDistance(
            World world,
            BlockPos position,
            BlockPos previous,
            int depth,
            Set<ChunkPos> missing,
            Map<BlockPos, Integer> samples) {
        int state = cachedRead(world, position, missing, samples);
        if (state == BLOCKED || state == UNAVAILABLE || state == WaterState.SOURCE) return Integer.MAX_VALUE;
        int below = cachedRead(world, offset(position, 0, -1, 0), missing, samples);
        if (below == WaterState.EMPTY || below > WaterState.SOURCE) return depth;
        if (below == UNAVAILABLE || depth == 4) return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        for (int direction = 0; direction < DX.length; direction++) {
            BlockPos next = offset(position, DX[direction], 0, DZ[direction]);
            if (next != null && !next.equals(previous))
                best = Math.min(best, dropDistance(world, next, position, depth + 1, missing, samples));
        }
        return best;
    }

    private static int cachedRead(
            World world, BlockPos position, Set<ChunkPos> missing, Map<BlockPos, Integer> samples) {
        if (position == null) return BLOCKED;
        Integer known = samples.get(position);
        if (known != null) return known;
        int value = read(world, position, missing);
        samples.put(position, value);
        return value;
    }

    private static int read(World world, BlockPos position, Set<ChunkPos> missing) {
        int state = state(world, position);
        if (state == UNAVAILABLE) missing.add(Coordinates.worldToChunk(position));
        return state;
    }

    /**
     * True for a source whose wake-up neighborhood, the cells below, above and beside it and beside the cell above,
     * holds only sources and blocking blocks. A source never changes, so updating any of those cells does nothing.
     */
    static boolean settledSource(World world, BlockPos position) {
        if (state(world, position) != WaterState.SOURCE) return false;
        if (!sourceOrBlocked(world, offset(position, 0, -1, 0))) return false;
        BlockPos above = offset(position, 0, 1, 0);
        if (!sourceOrBlocked(world, above)) return false;
        for (int direction = 0; direction < DX.length; direction++)
            if (!sourceOrBlocked(world, offset(position, DX[direction], 0, DZ[direction]))
                    || !sourceOrBlocked(world, offset(above, DX[direction], 0, DZ[direction]))) return false;
        return true;
    }

    private static boolean sourceOrBlocked(World world, BlockPos position) {
        int state = state(world, position);
        return state == WaterState.SOURCE || state == BLOCKED;
    }

    private static int state(World world, BlockPos position) {
        if (position == null || !WorldBounds.containsY(position.y())) return BLOCKED;
        ChunkPos chunk = Coordinates.worldToChunk(position);
        if (!world.hasChunk(chunk)) return UNAVAILABLE;
        int block = world.getBlock(position).orElseThrow();
        if (block == BlockId.AIR || PlantRules.isPlant((byte) block)) return WaterState.EMPTY;
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
