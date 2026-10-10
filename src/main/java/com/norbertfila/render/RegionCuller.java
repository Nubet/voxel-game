package com.norbertfila.render;

import com.norbertfila.streaming.RegionIndex;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RegionPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RegionCuller {
    public Result cull(Frustum frustum, RegionIndex index) {
        Objects.requireNonNull(frustum);
        Objects.requireNonNull(index);
        long start = System.nanoTime();
        List<ChunkPos> visible = new ArrayList<>();
        int regionTests = 0;
        int chunkTests = 0;
        int rejectedRegions = 0;
        for (RegionPos region : index.regions()) {
            regionTests++;
            if (!frustum.intersects(index.bounds(region))) {
                rejectedRegions++;
                continue;
            }
            for (ChunkPos chunk : index.chunks(region)) {
                chunkTests++;
                if (frustum.intersects(chunk)) visible.add(chunk);
            }
        }
        return new Result(visible, regionTests, chunkTests, rejectedRegions, System.nanoTime() - start);
    }

    public static List<ChunkPos> simpleVisible(Frustum frustum, RegionIndex index) {
        List<ChunkPos> visible = new ArrayList<>();
        for (RegionPos region : index.regions())
            for (ChunkPos chunk : index.chunks(region)) if (frustum.intersects(chunk)) visible.add(chunk);
        return List.copyOf(visible);
    }

    public record Result(
            List<ChunkPos> visible, int regionTests, int chunkTests, int rejectedRegions, long elapsedNanos) {
        public Result {
            visible = List.copyOf(visible);
        }
    }
}
