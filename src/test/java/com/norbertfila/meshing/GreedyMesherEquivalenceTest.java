package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** The allocation-free meshers must produce exactly the vertices of the original implementation. */
class GreedyMesherEquivalenceTest {
    private static final byte[] PALETTE = {
        BlockId.AIR,
        BlockId.AIR,
        BlockId.STONE,
        BlockId.GRASS,
        BlockId.DIRT,
        BlockId.WATER,
        BlockId.LEAVES,
        BlockId.GLASS,
        BlockId.LOG,
        BlockId.SHORT_GRASS,
        BlockId.POPPY,
        BlockId.SAND
    };

    @Test
    void terrainWithTreesWaterAndPlantsMatchesReference() {
        var generator = new TerrainGenerator(7_340_215L, TerrainConfig.defaults());
        World world = new World();
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                for (int y = 0; y <= 5; y++) {
                    ChunkPos position = new ChunkPos(x - 40, y, z + 11);
                    world.putChunk(position, generator.generate(position).toChunk());
                }
        int compared = 0;
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int y = 0; y <= 5; y++) {
                    assertSame(world, new ChunkPos(x - 40, y, z + 11));
                    compared++;
                }
        assertEquals(150, compared);
    }

    @Test
    void randomBlocksWithFlowingWaterAndPartialNeighborsMatchReference() {
        Random random = new Random(42);
        for (int trial = 0; trial < 40; trial++) {
            World world = new World();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx != 0 || dy != 0 || dz != 0) && random.nextInt(3) == 0) continue;
                        world.putChunk(new ChunkPos(dx - 1, dy, dz + 2), randomChunk(random, trial % 4 == 0));
                    }
            assertSame(world, new ChunkPos(-1, 0, 2));
        }
    }

    @Test
    void checkerboardAndSolidMatchReference() {
        World world = new World();
        Chunk checker = new Chunk();
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) if (((x + y + z) & 1) == 0) checker.set(x, y, z, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), checker);
        world.putChunk(new ChunkPos(1, 0, 0), Chunk.uniform(BlockId.STONE));
        assertSame(world, new ChunkPos(0, 0, 0));
        assertSame(world, new ChunkPos(1, 0, 0));
    }

    @Test
    void workersMeshConcurrentlyWithoutSharingScratch() throws Exception {
        Random random = new Random(7);
        List<World> worlds = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            World world = new World();
            world.putChunk(new ChunkPos(0, 0, 0), randomChunk(random, true));
            worlds.add(world);
        }
        try (var workers = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int round = 0; round < 4; round++)
                for (World world : worlds) tasks.add(workers.submit(() -> assertSame(world, new ChunkPos(0, 0, 0))));
            for (Future<?> task : tasks) task.get();
        }
    }

    private static Chunk randomChunk(Random random, boolean sparse) {
        Chunk chunk = new Chunk();
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    if (sparse && random.nextInt(4) != 0) continue;
                    byte block = PALETTE[random.nextInt(PALETTE.length)];
                    if (block == BlockId.WATER && random.nextBoolean())
                        chunk.set(x, y, z, block, WaterState.flowing(1 + random.nextInt(6)));
                    else chunk.set(x, y, z, block);
                }
        return chunk;
    }

    private static void assertSame(World world, ChunkPos position) {
        var neighborhood = world.blockNeighborhood(position);
        var center = neighborhood.center();
        int x = position.x(), y = position.y(), z = position.z();
        assertMesh(
                new ReferenceGreedyMesher().mesh(center, neighborhood, x, y, z),
                new GreedyMesher().mesh(center, neighborhood, x, y, z),
                position);
        assertMesh(
                new ReferenceGreedyMesher().meshTextured(center, neighborhood, x, y, z),
                new GreedyMesher().meshTextured(center, neighborhood, x, y, z),
                position);
        assertSplitWater(
                new ReferenceWaterMesher().mesh(center, neighborhood, x, y, z),
                new WaterMesher().mesh(center, neighborhood, x, y, z, ChunkMesh.ALL),
                position);
    }

    /** The old combined mesh held solids padded to the water layout followed by water; parts must match it. */
    private static void assertSplitWater(MeshData combined, ChunkMesh split, ChunkPos position) {
        int flow = MeshFormat.FLOW_TEXTURED_VERTEX_STRIDE, textured = MeshFormat.TEXTURED_VERTEX_STRIDE;
        var ranges = combined.ranges();
        int solidVertices = ranges.opaqueVertices() + ranges.cutoutVertices();
        float[] all = combined.vertices();
        float[] solid = new float[solidVertices * textured];
        for (int vertex = 0; vertex < solidVertices; vertex++)
            System.arraycopy(all, vertex * flow, solid, vertex * textured, textured);
        float[] water = java.util.Arrays.copyOfRange(all, solidVertices * flow, all.length);
        assertEquals(
                new QuadRanges(ranges.opaqueVertices() / 6, ranges.cutoutVertices() / 6, 0),
                split.solid().ranges());
        assertArrayEquals(
                solid,
                QuadMeshes.solid(split.solid(), position.x(), position.y(), position.z())
                        .vertices(),
                "solids at " + position);
        assertEquals(
                new QuadRanges(0, 0, ranges.translucentVertices() / 6),
                split.water().ranges());
        assertArrayEquals(
                water,
                QuadMeshes.water(split.water(), position.x(), position.y(), position.z())
                        .vertices(),
                1e-4f,
                "water at " + position);
    }

    private static void assertMesh(MeshData expected, MeshData actual, ChunkPos position) {
        assertEquals(expected.format(), actual.format(), "format at " + position);
        assertEquals(expected.ranges(), actual.ranges(), "ranges at " + position);
        assertArrayEquals(expected.vertices(), actual.vertices(), "vertices at " + position);
    }
}
