package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.RenderLayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FarTileMesherTest {
    @Test
    void flatTileIsOneTopQuadWithSkirtsOnEveryEdge() {
        FarTerrainTile tile = tile(8, (x, z) -> 20, null);
        QuadMesh mesh = FarTileMesher.mesh(tile, border(8, 20));

        List<Quad> quads = decode(mesh);
        assertEquals(
                1,
                quads.stream()
                        .filter(quad -> quad.face == FarTileMesher.FACE_TOP)
                        .count());
        Quad top = quads.stream()
                .filter(quad -> quad.face == FarTileMesher.FACE_TOP)
                .findFirst()
                .orElseThrow();
        assertEquals(21, top.y);
        assertEquals(64, top.width);
        assertEquals(64, top.height);
        assertEquals(4, quads.size() - 1, "one merged skirt per edge");
        for (Quad wall : quads)
            if (wall.face != FarTileMesher.FACE_TOP) {
                assertEquals(21 - FarTileMesher.SKIRT, wall.y);
                assertEquals(FarTileMesher.SKIRT, wall.height);
            }
        assertEquals(mesh.quadCount(), mesh.ranges().count(RenderLayer.OPAQUE));
    }

    @Test
    void stepBetweenCellsGetsOneWallSpanningTheDifference() {
        // Columns x < 4 at height 30, the rest at 20.
        FarTerrainTile tile = tile(8, (x, z) -> x < 4 ? 30 : 20, null);
        QuadMesh mesh = FarTileMesher.mesh(tile, border(8, 20));

        List<Quad> walls = decode(mesh).stream()
                .filter(quad -> quad.face == FarTileMesher.FACE_POSITIVE_X && quad.x == 32)
                .toList();
        assertEquals(1, walls.size(), "merged along z");
        assertEquals(21, walls.getFirst().y);
        assertEquals(10, walls.getFirst().height);
        assertEquals(64, walls.getFirst().width);
    }

    @Test
    void waterSurfacesFollowSolidQuadsAsTranslucent() {
        FarTerrainTile tile = tile(16, (x, z) -> 5, (x, z) -> x < 2 ? 9 : FarTerrainTile.NO_WATER);
        QuadMesh mesh = FarTileMesher.mesh(tile, border(16, 5));

        assertEquals(1, mesh.ranges().count(RenderLayer.TRANSLUCENT));
        Quad water = decode(mesh).getLast();
        assertTrue(water.water);
        assertEquals(10, water.y);
        assertEquals(32, water.height, "two cells of 16 along x");
    }

    @Test
    void cellsWithoutSurfaceHaveNoTopAndNeighborsWallDown() {
        FarTerrainTile tile = tile(32, (x, z) -> x == 0 && z == 0 ? FarTerrainTile.NO_SURFACE : 12, null);
        List<Quad> quads = decode(FarTileMesher.mesh(tile, border(32, 12)));

        assertEquals(
                2,
                quads.stream()
                        .filter(quad -> quad.face == FarTileMesher.FACE_TOP)
                        .count());
        assertTrue(quads.stream()
                .anyMatch(quad -> quad.face == FarTileMesher.FACE_NEGATIVE_X && quad.x == 32 && quad.height == 16));
    }

    @Test
    void verticalExtentCoversWallsAndTops() {
        FarTerrainTile tile = tile(8, (x, z) -> x < 4 ? 300 : -10, null);
        FarTileMesh mesh = FarTileMesh.of(tile, FarTileMesher.mesh(tile, border(8, -10)));

        assertTrue(mesh.minY() <= -10 - FarTileMesher.SKIRT);
        assertTrue(mesh.maxY() >= 301);
    }

    private interface Heights {
        int at(int x, int z);
    }

    private static FarTerrainTile tile(int step, Heights heights, Heights water) {
        int cells = FarTerrainTile.SIZE / step;
        int[] height = new int[cells * cells], waterTops = new int[height.length];
        byte[] materials = new byte[height.length];
        Arrays.fill(materials, BlockId.GRASS);
        for (int z = 0; z < cells; z++)
            for (int x = 0; x < cells; x++) {
                height[z * cells + x] = heights.at(x, z);
                waterTops[z * cells + x] = water == null ? FarTerrainTile.NO_WATER : water.at(x, z);
            }
        return new FarTerrainTile(2, -3, step, 0, height, materials, waterTops);
    }

    private static int[] border(int step, int height) {
        int[] border = new int[4 * (FarTerrainTile.SIZE / step)];
        Arrays.fill(border, height);
        return border;
    }

    private record Quad(int x, int y, int z, int face, boolean water, int width, int height, int texture) {}

    private static List<Quad> decode(QuadMesh mesh) {
        int[] records = mesh.records();
        List<Quad> quads = new ArrayList<>();
        for (int index = 0; index < records.length; index += 3) {
            int shape = records[index], extent = records[index + 2];
            quads.add(new Quad(
                    shape & 127,
                    records[index + 1],
                    shape >>> 7 & 127,
                    shape >>> 14 & 7,
                    (shape >>> 17 & 1) != 0,
                    (shape >>> 18 & 63) + 1,
                    (extent & 0xFFFF) + 1,
                    extent >>> 16));
        }
        return quads;
    }
}
