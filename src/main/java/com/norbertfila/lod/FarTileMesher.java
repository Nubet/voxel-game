package com.norbertfila.lod;

import com.norbertfila.meshing.IntList;
import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.meshing.QuadRanges;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;

/**
 * Turns a far tile into {@link QuadMesh.Format#FAR} records: merged top faces, walls where a cell stands above its
 * neighbor and flat water surfaces. Solid quads come first and are opaque; water quads follow as translucent.
 *
 * <p>Tile edges always get a wall that reaches {@link #SKIRT} blocks below the lower side, so tiles built at
 * different sample steps, whose edge heights need not agree, leave no gap between them.
 */
public final class FarTileMesher {
    public static final int SKIRT = 4;
    static final int FACE_POSITIVE_X = 0, FACE_NEGATIVE_X = 1, FACE_TOP = 2, FACE_POSITIVE_Z = 4, FACE_NEGATIVE_Z = 5;
    private static final int MAX_HEIGHT = 1 << 16;

    private FarTileMesher() {}

    /**
     * {@code border} holds the heights of the cells just outside the tile, {@code cells} per edge in the order west
     * (x = -1), east (x = cells), north (z = -1) and south (z = cells), each edge ordered by the other coordinate.
     */
    public static QuadMesh mesh(FarTerrainTile tile, int[] border) {
        int cells = tile.cells();
        if (border.length != 4 * cells) throw new IllegalArgumentException("Border needs four edges of cells");
        IntList records = new IntList();
        tops(tile, records, false);
        walls(tile, border, records);
        int solid = records.size() / QuadMesh.Format.FAR.ints();
        tops(tile, records, true);
        int water = records.size() / QuadMesh.Format.FAR.ints() - solid;
        return new QuadMesh(QuadMesh.Format.FAR, records.toArray(), new QuadRanges(solid, 0, water));
    }

    /** Greedy rectangles of cells sharing a height and material, or a water level when {@code water} is set. */
    private static void tops(FarTerrainTile tile, IntList records, boolean water) {
        int cells = tile.cells(), step = tile.sampleStep();
        boolean[] done = new boolean[cells * cells];
        for (int z = 0; z < cells; z++)
            for (int x = 0; x < cells; x++) {
                if (done[z * cells + x] || !hasTop(tile, x, z, water)) continue;
                int height = top(tile, x, z, water);
                byte material = water ? BlockId.WATER : tile.materialAt(x, z);
                int endX = x + 1;
                while (endX < cells && !done[z * cells + endX] && same(tile, endX, z, water, height, material)) endX++;
                int endZ = z + 1;
                while (endZ < cells && rowMatches(tile, done, x, endX, endZ, water, height, material)) endZ++;
                for (int row = z; row < endZ; row++)
                    for (int column = x; column < endX; column++) done[row * cells + column] = true;
                int texture = BlockRegistry.DEFAULT.definition(material).textureLayer(FACE_TOP);
                // The top face spans u along -z and v along +x, so its first corner is the far z edge.
                add(
                        records,
                        x * step,
                        height + 1,
                        endZ * step,
                        FACE_TOP,
                        water,
                        (endZ - z) * step,
                        (endX - x) * step,
                        texture);
            }
    }

    private static boolean rowMatches(
            FarTerrainTile tile, boolean[] done, int x, int endX, int z, boolean water, int height, byte material) {
        for (int column = x; column < endX; column++)
            if (done[z * tile.cells() + column] || !same(tile, column, z, water, height, material)) return false;
        return true;
    }

    private static boolean same(FarTerrainTile tile, int x, int z, boolean water, int height, byte material) {
        return hasTop(tile, x, z, water)
                && top(tile, x, z, water) == height
                && (water || tile.materialAt(x, z) == material);
    }

    private static boolean hasTop(FarTerrainTile tile, int x, int z, boolean water) {
        return water
                ? tile.waterTopAt(x, z) != FarTerrainTile.NO_WATER
                : tile.heightAt(x, z) != FarTerrainTile.NO_SURFACE;
    }

    private static int top(FarTerrainTile tile, int x, int z, boolean water) {
        return water ? tile.waterTopAt(x, z) : tile.heightAt(x, z);
    }

    /** Walls toward each of the four neighbors, merged along runs of cells with the same span and material. */
    private static void walls(FarTerrainTile tile, int[] border, IntList records) {
        int cells = tile.cells(), step = tile.sampleStep();
        for (int line = 0; line < cells; line++) {
            wallRun(tile, border, records, line, FACE_POSITIVE_X, cells, step);
            wallRun(tile, border, records, line, FACE_NEGATIVE_X, cells, step);
            wallRun(tile, border, records, line, FACE_POSITIVE_Z, cells, step);
            wallRun(tile, border, records, line, FACE_NEGATIVE_Z, cells, step);
        }
    }

    /**
     * Walls of one face direction along one line of cells: for x faces the line is a column x and cells run along z,
     * for z faces the line is a row z and cells run along x.
     */
    private static void wallRun(
            FarTerrainTile tile, int[] border, IntList records, int line, int face, int cells, int step) {
        boolean xFace = face == FACE_POSITIVE_X || face == FACE_NEGATIVE_X;
        int start = -1, runBottom = 0, runTop = 0, runTexture = 0;
        for (int along = 0; along <= cells; along++) {
            boolean present = false;
            int bottom = 0, top = 0, texture = 0;
            if (along < cells) {
                int x = xFace ? line : along, z = xFace ? along : line;
                int height = tile.heightAt(x, z);
                if (height != FarTerrainTile.NO_SURFACE) {
                    int nx = x + (face == FACE_POSITIVE_X ? 1 : face == FACE_NEGATIVE_X ? -1 : 0);
                    int nz = z + (face == FACE_POSITIVE_Z ? 1 : face == FACE_NEGATIVE_Z ? -1 : 0);
                    boolean edge = nx < 0 || nz < 0 || nx >= cells || nz >= cells;
                    int neighbor = edge ? border(border, cells, x, z, face) : tile.heightAt(nx, nz);
                    long low = neighbor == FarTerrainTile.NO_SURFACE ? (long) height - 16 : neighbor;
                    if (edge) low = Math.min(low, height) - SKIRT;
                    if (low < height) {
                        present = true;
                        top = height + 1;
                        bottom = (int) Math.max(low + 1, (long) top - MAX_HEIGHT);
                        texture = BlockRegistry.DEFAULT
                                .definition(tile.materialAt(x, z))
                                .textureLayer(face);
                    }
                }
            }
            boolean continues = present && start >= 0 && bottom == runBottom && top == runTop && texture == runTexture;
            if (continues) continue;
            if (start >= 0) emitWall(records, line, face, start, along, runBottom, runTop, runTexture, step);
            start = present ? along : -1;
            runBottom = bottom;
            runTop = top;
            runTexture = texture;
        }
    }

    private static void emitWall(
            IntList records, int line, int face, int start, int end, int bottom, int top, int texture, int step) {
        int width = (end - start) * step, height = top - bottom;
        int lineMin = line * step, lineMax = (line + 1) * step;
        int from = start * step, to = end * step;
        switch (face) {
            case FACE_POSITIVE_X -> add(records, lineMax, bottom, from, face, false, width, height, texture);
            case FACE_NEGATIVE_X -> add(records, lineMin, bottom, to, face, false, width, height, texture);
            case FACE_POSITIVE_Z -> add(records, to, bottom, lineMax, face, false, width, height, texture);
            case FACE_NEGATIVE_Z -> add(records, from, bottom, lineMin, face, false, width, height, texture);
            default -> throw new IllegalArgumentException("Not a wall face");
        }
    }

    private static int border(int[] border, int cells, int x, int z, int face) {
        return switch (face) {
            case FACE_NEGATIVE_X -> border[z];
            case FACE_POSITIVE_X -> border[cells + z];
            case FACE_NEGATIVE_Z -> border[2 * cells + x];
            case FACE_POSITIVE_Z -> border[3 * cells + x];
            default -> throw new IllegalArgumentException("Not a wall face");
        };
    }

    private static void add(
            IntList records, int x, int y, int z, int face, boolean water, int width, int height, int texture) {
        if (x < 0 || x > FarTerrainTile.SIZE || z < 0 || z > FarTerrainTile.SIZE)
            throw new IllegalArgumentException("Far quad corner outside the tile");
        if (width < 1 || width > FarTerrainTile.SIZE || height < 1 || height > MAX_HEIGHT || texture >>> 16 != 0)
            throw new IllegalArgumentException("Invalid far quad");
        records.add(x | z << 7 | face << 14 | (water ? 1 << 17 : 0) | (width - 1) << 18);
        records.add(y);
        records.add(height - 1 | texture << 16);
    }
}
