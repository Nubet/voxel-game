package com.norbertfila.lod;

import com.norbertfila.generation.TerrainSummary;
import com.norbertfila.world.BlockRegistry;

/**
 * Visual-only heightfield of one {@link #SIZE} by {@link #SIZE} block area, divided into square cells of
 * {@code sampleStep} blocks. Each cell stores its top solid block, that block's material and the top of the water
 * above it. Far terrain never feeds collision or simulation.
 *
 * <p>The heightfield represents only the topmost surface: caves, overhangs and blocks below a higher block are not
 * visible, and trees and plants are left out.
 */
public final class FarTerrainTile {
    public static final int SIZE = 64;
    /** Height of a column with no solid block, such as a shaft dug through bedrock. */
    public static final int NO_SURFACE = Integer.MIN_VALUE;
    /** Water height of a column without water above its surface. */
    public static final int NO_WATER = Integer.MIN_VALUE;

    private final int tileX;
    private final int tileZ;
    private final int sampleStep;
    private final int cells;
    private final long editRevision;
    private final int[] heights;
    private final byte[] materials;
    private final byte[] wallMaterials;
    private final WallColumn[] wallColumns;
    private final int[] waterTops;
    private final byte[] tints;
    private final int minHeight;
    private final int maxHeight;

    /** Takes ownership of the arrays, indexed {@code cellZ * cells + cellX}. */
    FarTerrainTile(
            int tileX, int tileZ, int sampleStep, long editRevision, int[] heights, byte[] materials, int[] waterTops) {
        this(tileX, tileZ, sampleStep, editRevision, heights, materials, waterTops, new byte[heights.length]);
    }

    FarTerrainTile(
            int tileX,
            int tileZ,
            int sampleStep,
            long editRevision,
            int[] heights,
            byte[] materials,
            int[] waterTops,
            byte[] tints) {
        this(tileX, tileZ, sampleStep, editRevision, heights, materials, waterTops, tints, materials);
    }

    FarTerrainTile(
            int tileX,
            int tileZ,
            int sampleStep,
            long editRevision,
            int[] heights,
            byte[] materials,
            int[] waterTops,
            byte[] tints,
            byte[] wallMaterials) {
        this(
                tileX,
                tileZ,
                sampleStep,
                editRevision,
                heights,
                materials,
                waterTops,
                tints,
                wallMaterials,
                new WallColumn[heights.length]);
    }

    FarTerrainTile(
            int tileX,
            int tileZ,
            int sampleStep,
            long editRevision,
            int[] heights,
            byte[] materials,
            int[] waterTops,
            byte[] tints,
            byte[] wallMaterials,
            WallColumn[] wallColumns) {
        if (!validStep(sampleStep)) throw new IllegalArgumentException("Sample step must divide the tile size");
        int cells = SIZE / sampleStep;
        if (heights.length != cells * cells || materials.length != heights.length || waterTops.length != heights.length)
            throw new IllegalArgumentException("Invalid far terrain cell count");
        this.tileX = tileX;
        this.tileZ = tileZ;
        this.sampleStep = sampleStep;
        this.cells = cells;
        this.editRevision = editRevision;
        this.heights = heights;
        this.materials = materials;
        if (wallMaterials.length != materials.length) throw new IllegalArgumentException("Invalid wall material grid");
        this.wallMaterials = wallMaterials;
        if (wallColumns.length != heights.length) throw new IllegalArgumentException("Invalid wall profile grid");
        this.wallColumns = wallColumns;
        this.waterTops = waterTops;
        if (tints.length != heights.length) throw new IllegalArgumentException("Invalid tint grid");
        this.tints = tints;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int index = 0; index < heights.length; index++) {
            int top = Math.max(heights[index], waterTops[index]);
            if (top == NO_SURFACE) continue;
            min = Math.min(min, top);
            max = Math.max(max, top);
        }
        minHeight = min;
        maxHeight = max;
    }

    public static boolean validStep(int sampleStep) {
        return sampleStep >= 1 && sampleStep <= SIZE && SIZE % sampleStep == 0;
    }

    public int tileX() {
        return tileX;
    }

    public int tileZ() {
        return tileZ;
    }

    public int originX() {
        return tileX * SIZE;
    }

    public int originZ() {
        return tileZ * SIZE;
    }

    public int sampleStep() {
        return sampleStep;
    }

    public int cells() {
        return cells;
    }

    /** Stamp of the authoritative edits this tile was built from, see {@link WorldChangeOverlay#areaRevision}. */
    public long editRevision() {
        return editRevision;
    }

    public int heightAt(int cellX, int cellZ) {
        return heights[index(cellX, cellZ)];
    }

    public byte materialAt(int cellX, int cellZ) {
        return materials[index(cellX, cellZ)];
    }

    public byte wallMaterialAt(int x, int z) {
        return wallMaterials[index(x, z)];
    }

    byte wallMaterialAt(int x, int z, int worldY) {
        int index = index(x, z);
        var column = wallColumns[index];
        return column == null ? wallMaterials[index] : column.materialAt(worldY, wallMaterials[index]);
    }

    boolean layeredWallAt(int x, int z) {
        return wallColumns[index(x, z)] != null;
    }

    public int waterTopAt(int cellX, int cellZ) {
        return waterTops[index(cellX, cellZ)];
    }

    public int tintAt(int x, int z) {
        return tints[index(x, z)];
    }

    /** Lowest surface or water top in the tile, or {@link Integer#MAX_VALUE} when no cell has either. */
    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public long estimatedBytes() {
        long bytes = (long) heights.length * (2 * Integer.BYTES + 3 + Long.BYTES);
        AreaEdits edits = null;
        for (var column : wallColumns) {
            if (column == null) continue;
            bytes += 120;
            edits = column.edits();
        }
        if (edits != null)
            for (int column = 0; column < edits.columnCount(); column++)
                bytes += 12L + edits.changeCount(column) * (long) Long.BYTES;
        return bytes;
    }

    record WallColumn(TerrainSummary terrain, AreaEdits edits, int editColumn) {
        byte materialAt(int worldY, byte fallback) {
            int changes = editColumn < 0 ? 0 : edits.changeCount(editColumn);
            for (int change = 0; change < changes; change++) {
                int y = edits.changeY(editColumn, change);
                if (y < worldY) break;
                if (y == worldY && BlockRegistry.DEFAULT.isCollidable(edits.changeBlock(editColumn, change)))
                    return edits.changeBlock(editColumn, change);
            }
            if (worldY > terrain.surfaceHeight()) return fallback;
            // Far heightfields fill air gaps below the top surface
            byte material = terrain.materialAt(worldY);
            return BlockRegistry.DEFAULT.isCollidable(material) ? material : fallback;
        }
    }

    private int index(int cellX, int cellZ) {
        if (cellX < 0 || cellX >= cells || cellZ < 0 || cellZ >= cells)
            throw new IndexOutOfBoundsException("Far terrain cell outside tile");
        return cellZ * cells + cellX;
    }
}
