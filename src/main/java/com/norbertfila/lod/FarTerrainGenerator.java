package com.norbertfila.lod;

import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.TerrainSummary;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import java.util.Arrays;
import java.util.Objects;

/**
 * Builds far tiles from per-column terrain summaries and an edit snapshot, without generating any chunk. Safe to call
 * from worker threads: the source is thread-safe and the edits are immutable.
 *
 * <p>Each cell takes the generated column at its center with that column's edits applied. Edited columns elsewhere in
 * the cell can only raise it, so buildings stay visible from afar, while digging off the center column is not shown.
 */
public final class FarTerrainGenerator {
    private static final BlockRegistry BLOCKS = BlockRegistry.DEFAULT;

    private final TerrainSource source;

    public FarTerrainGenerator(TerrainSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public FarTerrainTile generate(int tileX, int tileZ, int sampleStep, AreaEdits edits) {
        if (!FarTerrainTile.validStep(sampleStep))
            throw new IllegalArgumentException("Sample step must divide the tile size");
        int originX = Math.multiplyExact(tileX, FarTerrainTile.SIZE);
        int originZ = Math.multiplyExact(tileZ, FarTerrainTile.SIZE);
        if (!edits.covers(originX, originZ, FarTerrainTile.SIZE))
            throw new IllegalArgumentException("Edits do not cover the tile");
        int cells = FarTerrainTile.SIZE / sampleStep;
        int[] heights = new int[cells * cells];
        byte[] materials = new byte[heights.length];
        int[] waterTops = new int[heights.length];
        Arrays.fill(waterTops, FarTerrainTile.NO_WATER);
        int center = sampleStep / 2;
        for (int cellZ = 0; cellZ < cells; cellZ++)
            for (int cellX = 0; cellX < cells; cellX++) {
                int x = originX + cellX * sampleStep + center, z = originZ + cellZ * sampleStep + center;
                TerrainSummary terrain = source.sample(x, z);
                int column = edits.find(x, z);
                int index = cellZ * cells + cellX;
                long surface = surface(terrain, edits, column);
                heights[index] = (int) (surface >> 8);
                materials[index] = (byte) surface;
                waterTops[index] = waterTop(terrain, edits, column, heights[index]);
            }
        for (int column = 0; column < edits.columnCount(); column++) {
            int localX = edits.columnX(column) - originX, localZ = edits.columnZ(column) - originZ;
            if (localX % sampleStep == center && localZ % sampleStep == center) continue;
            int index = localZ / sampleStep * cells + localX / sampleStep;
            for (int change = 0; change < edits.changeCount(column); change++) {
                byte block = edits.changeBlock(column, change);
                if (!BLOCKS.isCollidable(block)) continue;
                int y = edits.changeY(column, change);
                if (y > heights[index]) {
                    heights[index] = y;
                    materials[index] = block;
                    if (waterTops[index] <= y) waterTops[index] = FarTerrainTile.NO_WATER;
                }
                break;
            }
        }
        return new FarTerrainTile(tileX, tileZ, sampleStep, edits.revision(), heights, materials, waterTops);
    }

    /**
     * Generated heights of the cells just outside a tile in the layout {@link FarTileMesher#mesh} expects. Edits are
     * left out; a neighbor tile draws its own edited walls, and the edge skirt hides small disagreements.
     */
    public int[] border(int tileX, int tileZ, int sampleStep) {
        if (!FarTerrainTile.validStep(sampleStep))
            throw new IllegalArgumentException("Sample step must divide the tile size");
        int cells = FarTerrainTile.SIZE / sampleStep, center = sampleStep / 2;
        int originX = Math.multiplyExact(tileX, FarTerrainTile.SIZE) + center;
        int originZ = Math.multiplyExact(tileZ, FarTerrainTile.SIZE) + center;
        int[] border = new int[4 * cells];
        for (int cell = 0; cell < cells; cell++) {
            int along = cell * sampleStep;
            border[cell] = source.surfaceHeight(originX - sampleStep, originZ + along);
            border[cells + cell] = source.surfaceHeight(originX + FarTerrainTile.SIZE, originZ + along);
            border[2 * cells + cell] = source.surfaceHeight(originX + along, originZ - sampleStep);
            border[3 * cells + cell] = source.surfaceHeight(originX + along, originZ + FarTerrainTile.SIZE);
        }
        return border;
    }

    /**
     * Highest solid block of a column with its edits applied, packed as {@code height << 8 | material}. Walks down
     * from the highest change, jumping over generated air so a tower or a deep shaft costs one step per change.
     */
    private static long surface(TerrainSummary terrain, AreaEdits edits, int column) {
        int changes = column < 0 ? 0 : edits.changeCount(column);
        int surface = terrain.surfaceHeight();
        long y = changes > 0 ? Math.max(surface, edits.changeY(column, 0)) : surface;
        int change = 0;
        while (true) {
            while (change < changes && edits.changeY(column, change) > y) change++;
            byte block;
            if (change < changes && edits.changeY(column, change) == y) {
                block = edits.changeBlock(column, change);
            } else {
                block = terrain.materialAt((int) y);
                if (!BLOCKS.isCollidable(block)) {
                    // Generated columns are air or water above the surface and air below bedrock.
                    long next = change < changes ? edits.changeY(column, change) : Long.MIN_VALUE;
                    if (y > surface) next = Math.max(next, surface);
                    if (next == Long.MIN_VALUE) return (long) FarTerrainTile.NO_SURFACE << 8;
                    y = next;
                    continue;
                }
            }
            if (BLOCKS.isCollidable(block)) return y << 8 | Byte.toUnsignedLong(block);
            y--;
        }
    }

    /** Highest water block above {@code floor}, the column's surface, or {@link FarTerrainTile#NO_WATER}. */
    private static int waterTop(TerrainSummary terrain, AreaEdits edits, int column, int floor) {
        int changes = column < 0 ? 0 : edits.changeCount(column);
        int waterLevel = terrain.waterLevel();
        long y = Math.max(changes > 0 ? edits.changeY(column, 0) : Long.MIN_VALUE, waterLevel);
        int change = 0;
        while (y > floor) {
            while (change < changes && edits.changeY(column, change) > y) change++;
            byte block = change < changes && edits.changeY(column, change) == y
                    ? edits.changeBlock(column, change)
                    : terrain.materialAt((int) y);
            if (block == BlockId.WATER) return (int) y;
            if (change < changes && edits.changeY(column, change) == y) {
                y--;
                continue;
            }
            long next = change < changes ? edits.changeY(column, change) : Long.MIN_VALUE;
            if (y > waterLevel) next = Math.max(next, waterLevel);
            y = next;
        }
        return FarTerrainTile.NO_WATER;
    }
}
