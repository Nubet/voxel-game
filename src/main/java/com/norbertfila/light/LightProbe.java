package com.norbertfila.light;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldBounds;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Computes light at single positions on demand, the way Minecraft would propagate it: one level is lost per step
 * through blocks that transmit light, and sky light enters from columns open to the sky.
 */
public final class LightProbe implements LightLevels {
    private static final int RADIUS = MAX_LEVEL - 1;
    private static final int EDGE = 2 * RADIUS + 1;
    private static final int CELLS = EDGE * EDGE * EDGE;
    private static final int SAFE_COORDINATE = Integer.MAX_VALUE - 2 * Chunk.SIZE;
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private final World world;
    private final SkyExposure sky;
    private final LightSources sources;
    private final BlockReader reader;
    private final int[] visited = new int[CELLS];
    private final int[] emitterStamp = new int[CELLS];
    private final int[] emitterLevel = new int[CELLS];
    private final int[] queue = new int[CELLS];
    private int stamp;

    public LightProbe(World world, SkyExposure sky) {
        this.world = Objects.requireNonNull(world);
        this.sky = Objects.requireNonNull(sky);
        sources = new LightSources(world);
        reader = new BlockReader(world);
    }

    @Override
    public OptionalInt skyLight(BlockPos position) {
        world.assertOwner();
        if (!safe(position)) return OptionalInt.empty();
        OptionalInt top = sky.top(position.x(), position.z());
        if (top.isEmpty()) return OptionalInt.empty();
        if (position.y() > top.getAsInt()) return OptionalInt.of(MAX_LEVEL);
        return search(position, false, 0);
    }

    @Override
    public OptionalInt blockLight(BlockPos position) {
        world.assertOwner();
        if (!safe(position)) return OptionalInt.empty();
        int strongest = collectEmitters(position);
        if (strongest < 0) return OptionalInt.empty();
        if (strongest == 0) return OptionalInt.of(0);
        return search(position, true, strongest);
    }

    /** Marks emitters that could reach the position and returns their highest level, or -1 for missing chunks. */
    private int collectEmitters(BlockPos center) {
        nextStamp();
        int strongest = 0;
        int minChunkY = Math.max(Math.floorDiv(center.y() - RADIUS, Chunk.SIZE), WorldBounds.MIN_CHUNK_Y);
        int maxChunkY = Math.min(Math.floorDiv(center.y() + RADIUS, Chunk.SIZE), WorldBounds.MAX_CHUNK_Y);
        for (int cx = Math.floorDiv(center.x() - RADIUS, Chunk.SIZE);
                cx <= Math.floorDiv(center.x() + RADIUS, Chunk.SIZE);
                cx++)
            for (int cz = Math.floorDiv(center.z() - RADIUS, Chunk.SIZE);
                    cz <= Math.floorDiv(center.z() + RADIUS, Chunk.SIZE);
                    cz++)
                for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                    int[] emitters = sources.emitters(new ChunkPos(cx, cy, cz));
                    if (emitters == null) return -1;
                    for (int packed : emitters) {
                        int dx = cx * Chunk.SIZE + LightSources.localX(packed) - center.x();
                        int dy = cy * Chunk.SIZE + LightSources.localY(packed) - center.y();
                        int dz = cz * Chunk.SIZE + LightSources.localZ(packed) - center.z();
                        int level = LightSources.level(packed);
                        if (level - Math.abs(dx) - Math.abs(dy) - Math.abs(dz) <= 0) continue;
                        int cell = cell(dx, dy, dz);
                        emitterStamp[cell] = stamp;
                        emitterLevel[cell] = level;
                        strongest = Math.max(strongest, level);
                    }
                }
        return strongest;
    }

    /** Breadth-first order lets sky light stop at the first open-sky cell, because that one is the nearest. */
    private OptionalInt search(BlockPos center, boolean blockLight, int strongest) {
        if (!blockLight) nextStamp();
        reader.reset();
        int head = 0, tail = 0, best = 0;
        int origin = cell(0, 0, 0);
        visited[origin] = stamp;
        queue[tail++] = origin;
        while (head < tail) {
            int entry = queue[head++];
            int cell = entry & 0xffff, depth = entry >>> 16;
            if (blockLight) {
                if (strongest - depth <= best) break;
                if (emitterStamp[cell] == stamp) best = Math.max(best, emitterLevel[cell] - depth);
            }
            if (depth == RADIUS) continue;
            int dx = cell % EDGE - RADIUS, dz = cell / EDGE % EDGE - RADIUS, dy = cell / (EDGE * EDGE) - RADIUS;
            if (depth > 0 && !BlockRegistry.DEFAULT.transmitsLight((byte) block(center, dx, dy, dz))) continue;
            for (int[] step : STEPS) {
                int nx = dx + step[0], ny = dy + step[1], nz = dz + step[2];
                if (Math.abs(nx) + Math.abs(ny) + Math.abs(nz) > RADIUS) continue;
                int next = cell(nx, ny, nz);
                if (visited[next] == stamp) continue;
                visited[next] = stamp;
                int block = block(center, nx, ny, nz);
                if (block == BlockReader.UNKNOWN) {
                    if (blockLight) continue;
                    return OptionalInt.empty();
                }
                boolean transmits = BlockRegistry.DEFAULT.transmitsLight((byte) block);
                if (!blockLight && transmits) {
                    int y = center.y() + ny;
                    OptionalInt top = sky.top(center.x() + nx, center.z() + nz);
                    if (top.isEmpty()) return OptionalInt.empty();
                    if (y > top.getAsInt()) return OptionalInt.of(MAX_LEVEL - depth - 1);
                }
                if (transmits || blockLight && emitterStamp[next] == stamp) queue[tail++] = next | (depth + 1) << 16;
            }
        }
        return OptionalInt.of(best);
    }

    private int block(BlockPos center, int dx, int dy, int dz) {
        return reader.block(center.x() + dx, center.y() + dy, center.z() + dz);
    }

    private void nextStamp() {
        if (++stamp == 0) {
            Arrays.fill(visited, 0);
            Arrays.fill(emitterStamp, 0);
            stamp = 1;
        }
    }

    private static int cell(int dx, int dy, int dz) {
        return ((dy + RADIUS) * EDGE + dz + RADIUS) * EDGE + dx + RADIUS;
    }

    private static boolean safe(BlockPos position) {
        return Math.abs((long) position.x()) < SAFE_COORDINATE && Math.abs((long) position.z()) < SAFE_COORDINATE;
    }
}
