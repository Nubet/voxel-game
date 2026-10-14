package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.WaterSurface;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class WaterMesherTest {
    @Test
    void flatSourceLakeIsEightNinthsHighAndMissingDataDoesNotBecomeAir() {
        var lake = WaterSurface.corners((x, y, z) -> y == 0 ? WaterState.SOURCE : WaterSurface.EMPTY, 0, 0, 0);
        assertEquals(8 / 9f, lake.h00(), 1e-7);
        assertEquals(lake.h00(), lake.h11());
        var provisional = WaterSurface.corners(
                (x, y, z) -> y == 0 && x == 0 && z == 0 ? WaterState.SOURCE : WaterSurface.UNKNOWN, 0, 0, 0);
        assertEquals(8 / 9f, provisional.h00(), 1e-7);
        assertThrows(IllegalArgumentException.class, () -> WaterSurface.height(WaterSurface.UNKNOWN));
    }

    @Test
    void sourceAndSuccessiveFlowLevelsHaveDecreasingFractionalHeight() {
        float previous = 1;
        for (byte state = 0; state <= 7; state++) {
            float height = WaterSurface.height(state);
            assertTrue(height > 0 && height < previous);
            previous = height;
            World world = world();
            wet(world, 8, 8, 8, state);
            MeshData mesh = mesh(world, new ChunkPos(0, 0, 0));
            assertEquals(MeshFormat.FLOW_TEXTURED_VERSION, mesh.format().version());
            assertEquals(9, mesh.format().stride());
            assertTrue(
                    mesh.format().bounds().maxY() > 8 && mesh.format().bounds().maxY() < 9);
            assertEquals(36, mesh.ranges().translucentVertices());
        }
        assertEquals(8 / 9f, WaterSurface.height(WaterState.FALLING));
    }

    @Test
    void adjacentLevelsHaveContinuousEdgesAndNoInternalWall() {
        World world = world();
        wet(world, 8, 8, 8, WaterState.SOURCE);
        wet(world, 9, 8, 8, WaterState.flowing(7));
        var n = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        var left = WaterSurface.corners(n::waterSample, 8, 8, 8);
        var right = WaterSurface.corners(n::waterSample, 9, 8, 8);
        assertEquals(left.h10(), right.h00());
        assertEquals(left.h11(), right.h01());
        assertTrue(left.h00() > right.h10());
        var mesh = mesh(world, new ChunkPos(0, 0, 0));
        assertEquals(60, mesh.ranges().translucentVertices());
        assertTrue(mesh.format().bounds().maxY() < 9);
    }

    @Test
    void diagonalHaloMakesChunkCornerHeightsIdenticalAndSnapshotsStayImmutable() {
        World world = world();
        for (int x = -1; x <= 0; x++)
            for (int z = -1; z <= 0; z++) wet(world, x, 15, z, WaterState.flowing(x == -1 ? 2 : 7));
        wet(world, -1, 16, -1, WaterState.SOURCE);
        var a = world.blockNeighborhood(new ChunkPos(-1, 0, -1));
        var b = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        float corner = WaterSurface.corners(a::waterSample, 15, 15, 15).h11();
        assertEquals(1, corner);
        assertEquals(corner, WaterSurface.corners(b::waterSample, 0, 15, 0).h00());
        long oldRevision = b.revision();
        new WorldEdit(world, 64).setBlock(new BlockPos(-1, 16, -1), BlockId.AIR);
        assertEquals(1, WaterSurface.corners(b::waterSample, 0, 15, 0).h00());
        var fresh = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        assertNotEquals(oldRevision, fresh.revision());
        assertTrue(WaterSurface.corners(fresh::waterSample, 0, 15, 0).h00() < 1);
    }

    @Test
    void stackedWaterHasFullLowerCellAndNoHorizontalInternalFace() {
        World world = world();
        wet(world, 8, 8, 8, WaterState.FALLING);
        wet(world, 8, 9, 8, WaterState.FALLING);
        var n = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        var lower = WaterSurface.corners(n::waterSample, 8, 8, 8);
        assertEquals(1, lower.h00());
        assertEquals(1, lower.h11());
        assertEquals(60, mesh(world, new ChunkPos(0, 0, 0)).ranges().translucentVertices());
    }

    @Test
    void solidsKeepGreedyRangesAndOnlyExposedWaterFacesAreEmitted() {
        World world = world();
        wet(world, 8, 8, 8, WaterState.SOURCE);
        world.setBlock(new BlockPos(8, 7, 8), BlockId.STONE, 64);
        world.setBlock(new BlockPos(7, 8, 8), BlockId.STONE, 64);
        world.setBlock(new BlockPos(10, 8, 8), BlockId.LEAVES, 64);
        var n = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        var result = new WaterMesher().mesh(n.center(), n, 0, 0, 0, ChunkMesh.ALL);
        assertTrue(result.solid().ranges().opaque() > 0);
        assertTrue(result.solid().ranges().cutout() > 0);
        assertEquals(0, result.solid().ranges().translucent());
        assertEquals(QuadMesh.Format.SOLID, result.solid().format());
        assertEquals(new QuadRanges(0, 0, 4), result.water().ranges());
        assertEquals(QuadMesh.Format.WATER, result.water().format());
        var waterOnly = new WaterMesher().mesh(n.center(), n, 0, 0, 0, ChunkMesh.WATER);
        assertEquals(null, waterOnly.solid());
        assertArrayEquals(result.water().records(), waterOnly.water().records());
    }

    @Test
    void topTrianglesPointUpAndInterpolationMatchesTheirDiagonal() {
        World world = world();
        wet(world, 8, 8, 8, WaterState.SOURCE);
        wet(world, 9, 8, 8, WaterState.flowing(7));
        var n = world.blockNeighborhood(new ChunkPos(0, 0, 0));
        var heights = WaterSurface.corners(n::waterSample, 8, 8, 8);
        assertEquals(heights.h00(), heights.heightAt(0, 0));
        assertEquals((heights.h01() + heights.h10()) / 2d, heights.heightAt(.5, .5), 1e-7);
        assertEquals(heights.h11(), heights.heightAt(1, 1));
        float[] vertices = mesh(world, new ChunkPos(0, 0, 0)).vertices();
        int tops = 0;
        for (int i = 0; i < vertices.length; i += 27) {
            if (vertices[i + 6] != 1) continue;
            float ax = vertices[i + 9] - vertices[i], az = vertices[i + 11] - vertices[i + 2];
            float bx = vertices[i + 18] - vertices[i], bz = vertices[i + 20] - vertices[i + 2];
            assertTrue(az * bx - ax * bz > 0);
            tops++;
        }
        assertEquals(4, tops);
        assertThrows(IllegalArgumentException.class, () -> heights.heightAt(-1, 0));
    }

    private static World world() {
        World world = new World();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) world.putChunk(new ChunkPos(x, y, z), new Chunk());
        return world;
    }

    private static void wet(World world, int x, int y, int z, byte state) {
        world.setBlock(new BlockPos(x, y, z), BlockId.WATER, state, 64);
    }

    private static MeshData mesh(World world, ChunkPos position) {
        var n = world.blockNeighborhood(position);
        return QuadMeshes.water(
                new WaterMesher()
                        .mesh(n.center(), n, position.x(), position.y(), position.z(), ChunkMesh.WATER)
                        .water(),
                position.x(),
                position.y(),
                position.z());
    }
}
