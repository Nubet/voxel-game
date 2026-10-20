package com.norbertfila.weather;

import com.norbertfila.world.*;
import java.util.*;

public final class PrecipitationField {
    public static final int RADIUS = 24;
    public static final int BUDGET = 64;
    private final World world;
    private final BiomeWeather biomes;
    private final int radius;
    private final Map<Long, PrecipitationColumn> columns = new LinkedHashMap<>();
    private final Set<Long> pending = new LinkedHashSet<>();
    private List<PrecipitationColumn> snapshot = List.of();
    private boolean dirty;
    private int centerX, centerZ;
    private boolean centered;

    public PrecipitationField(World world, BiomeWeather biomes) {
        this(world, biomes, RADIUS);
    }

    public PrecipitationField(World world, BiomeWeather biomes, int radius) {
        this.world = Objects.requireNonNull(world);
        this.biomes = Objects.requireNonNull(biomes);
        if (radius < 1 || radius > 32) throw new IllegalArgumentException("Invalid precipitation radius");
        this.radius = radius;
    }

    public int update(double x, double z, boolean active) {
        world.assertOwner();
        if (!Double.isFinite(x)
                || !Double.isFinite(z)
                || x < (long) Integer.MIN_VALUE + radius
                || z < (long) Integer.MIN_VALUE + radius
                || x > (long) Integer.MAX_VALUE - radius - 16
                || z > (long) Integer.MAX_VALUE - radius - 16)
            throw new IllegalArgumentException("Invalid precipitation center");
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        if (!centered || cx != centerX || cz != centerZ) {
            centerX = cx;
            centerZ = cz;
            centered = true;
            columns.keySet().removeIf(key -> !contains(x(key), z(key)));
            pending.removeIf(key -> !contains(x(key), z(key)));
            // Center-first admission keeps newly loaded rain near the player within the same fixed work budget.
            for (int ring = 0; ring <= radius; ring++)
                for (int dz = -ring; dz <= ring; dz++)
                    for (int dx = -ring; dx <= ring; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !contains(cx + dx, cz + dz)) continue;
                        long key = key(cx + dx, cz + dz);
                        if (!columns.containsKey(key)) pending.add(key);
                    }
            dirty = true;
        }
        int processed = 0;
        if (active) {
            var iterator = pending.iterator();
            while (iterator.hasNext() && processed < BUDGET) {
                long key = iterator.next();
                iterator.remove();
                var column = scan(x(key), z(key));
                if (column != null) columns.put(key, column);
                dirty = true;
                processed++;
            }
        }
        if (dirty) {
            snapshot = List.copyOf(columns.values());
            dirty = false;
        }
        return processed;
    }

    public List<PrecipitationColumn> snapshot() {
        world.assertOwner();
        return snapshot;
    }

    public void onBlockChanged(BlockMutation mutation) {
        invalidate(mutation.position().x(), mutation.position().z());
    }

    public void onChunkChanged(ChunkPos chunk) {
        world.assertOwner();
        long originX = (long) chunk.x() * Chunk.SIZE, originZ = (long) chunk.z() * Chunk.SIZE;
        for (int dz = 0; dz < Chunk.SIZE; dz++)
            for (int dx = 0; dx < Chunk.SIZE; dx++) {
                long x = originX + dx, z = originZ + dz;
                if (x >= Integer.MIN_VALUE
                        && x <= Integer.MAX_VALUE
                        && z >= Integer.MIN_VALUE
                        && z <= Integer.MAX_VALUE) invalidate((int) x, (int) z);
            }
    }

    private void invalidate(int x, int z) {
        world.assertOwner();
        if (!centered || !contains(x, z)) return;
        long key = key(x, z);
        columns.remove(key);
        pending.add(key);
        dirty = true;
    }

    private PrecipitationColumn scan(int x, int z) {
        int cx = Math.floorDiv(x, Chunk.SIZE), cz = Math.floorDiv(z, Chunk.SIZE);
        int lx = Math.floorMod(x, Chunk.SIZE), lz = Math.floorMod(z, Chunk.SIZE);
        for (int cy = WorldBounds.MAX_CHUNK_Y; cy >= WorldBounds.MIN_CHUNK_Y; cy--) {
            var chunk = world.getChunk(new ChunkPos(cx, cy, cz));
            if (chunk == null) return null;
            if (chunk.uniformBlock() == BlockId.AIR) continue;
            for (int ly = Chunk.SIZE - 1; ly >= 0; ly--) {
                byte block = chunk.get(lx, ly, lz);
                var definition = BlockRegistry.DEFAULT.definition(block);
                if (!definition.collidable() && !BlockId.isFluid(Byte.toUnsignedInt(block))) continue;
                float top = definition.model().maxY();
                return new PrecipitationColumn(x, z, cy * Chunk.SIZE + ly + top, biomes.sample(x + .5, z + .5));
            }
        }
        return new PrecipitationColumn(x, z, WorldBounds.MIN_Y, biomes.sample(x + .5, z + .5));
    }

    private boolean contains(int x, int z) {
        long dx = (long) x - centerX, dz = (long) z - centerZ;
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) return false;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(z);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }
}
