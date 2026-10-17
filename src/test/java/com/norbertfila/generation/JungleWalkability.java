package com.norbertfila.generation;

import com.norbertfila.world.*;
import java.util.*;

final class JungleWalkability {
    static int largest(World world, TerrainSource terrain, int ox, int oz, int size) {
        var heights = new HashMap<BlockPos, Integer>();
        for (int z = oz; z < oz + size; z++)
            for (int x = ox; x < ox + size; x++) {
                int y = terrain.surfaceHeight(x, z);
                var feet = world.getBlock(new BlockPos(x, y + 1, z));
                var head = world.getBlock(new BlockPos(x, y + 2, z));
                var floor = world.getBlock(new BlockPos(x, y, z));
                if (feet.isPresent()
                        && head.isPresent()
                        && floor.isPresent()
                        && !BlockRegistry.DEFAULT.isCollidable((byte) feet.getAsInt())
                        && !BlockRegistry.DEFAULT.isCollidable((byte) head.getAsInt())
                        && BlockRegistry.DEFAULT.isCollidable((byte) floor.getAsInt()))
                    heights.put(new BlockPos(x, 0, z), y);
            }
        var visited = new HashSet<BlockPos>();
        int largest = 0;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (var start : heights.keySet()) {
            var queue = new ArrayDeque<BlockPos>();
            queue.add(start);
            int count = 0;
            while (!queue.isEmpty()) {
                var p = queue.remove();
                if (!visited.add(p)) continue;
                count++;
                for (int[] d : sides) {
                    var next = new BlockPos(p.x() + d[0], 0, p.z() + d[1]);
                    if (heights.containsKey(next) && Math.abs(heights.get(next) - heights.get(p)) <= 1) queue.add(next);
                }
            }
            largest = Math.max(largest, count);
        }
        return largest;
    }
}
