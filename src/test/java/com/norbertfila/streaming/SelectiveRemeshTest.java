package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SelectiveRemeshTest {
    private static final ChunkPos CENTER = new ChunkPos(0, 0, 0);

    @Test
    void waterLevelChangeRebuildsOnlyWaterSurfaces() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.settle();
            fixture.world.setBlock(new BlockPos(4, 5, 4), BlockId.WATER, WaterState.flowing(3), 8);
            fixture.manager.requestWaterRemesh(List.of(CENTER));
            ChunkMesh mesh = fixture.nextMesh(CENTER);
            assertNull(mesh.solid(), "solid geometry does not depend on water levels");
            assertNotNull(mesh.water());
        }
    }

    @Test
    void blockTypeChangesRebuildBothParts() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.settle();
            var change = new WorldEdit(fixture.world, 8).setBlock(new BlockPos(4, 5, 4), BlockId.STONE);
            fixture.manager.requestRemesh(change);
            ChunkMesh mesh = fixture.nextMesh(CENTER);
            assertNotNull(mesh.solid());
            assertNotNull(mesh.water());
        }
    }

    @Test
    void levelChangeQueuedBehindBlockChangeIsMergedIntoOneFullRebuild() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.settle();
            fixture.manager.requestWaterRemesh(List.of(CENTER));
            fixture.manager.requestRemesh(List.of(CENTER));
            ChunkMesh mesh = fixture.nextMesh(CENTER);
            assertNotNull(mesh.solid());
            assertNotNull(mesh.water());
        }
    }

    @Test
    void firstMeshOfAChunkAlwaysContainsEveryPart() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.manager.update(CENTER);
            fixture.manager.requestWaterRemesh(List.of(CENTER));
            ChunkMesh mesh = fixture.nextMesh(CENTER);
            assertEquals(ChunkMesh.ALL, mesh.parts());
        }
    }

    /** A stone floor with a pond: every section is the same so the center chunk has all neighbors. */
    private static final class Fixture implements AutoCloseable {
        final World world = new World();
        final ChunkManager manager = new ChunkManager(
                world, new Pond(), new WaterMesher(), new ViewDemand(1, 0, 0, ViewDemand.Shape.SQUARE));
        final Map<ChunkPos, ChunkManager.MeshResult> uploaded = new HashMap<>();

        void settle() throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            do {
                assertTrue(System.nanoTime() < deadline, "streaming did not settle");
                pump();
            } while (uploaded.size() < 9
                    || manager.metrics().meshOutstanding() > 0
                    || manager.metrics().pendingRemesh() > 0);
        }

        ChunkMesh nextMesh(ChunkPos position) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                manager.update(CENTER);
                for (var mesh : manager.processResults().meshes()) {
                    assertTrue(manager.markUploaded(mesh));
                    if (mesh.position().equals(position)) return mesh.mesh();
                }
                Thread.sleep(1);
            }
            throw new AssertionError("No mesh for " + position);
        }

        private void pump() throws InterruptedException {
            manager.update(CENTER);
            for (var mesh : manager.processResults().meshes()) {
                assertTrue(manager.markUploaded(mesh));
                uploaded.put(mesh.position(), mesh);
            }
            Thread.sleep(1);
        }

        @Override
        public void close() {
            manager.close();
        }
    }

    private static final class Pond implements WorldGenerator {
        @Override
        public long seed() {
            return 0;
        }

        @Override
        public int generatorVersion() {
            return 1;
        }

        @Override
        public ChunkData generate(ChunkPos position) {
            byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 4; y++) blocks[(y * 16 + z) * 16 + x] = BlockId.STONE;
                    if (x > 2 && x < 8 && z > 2 && z < 8) blocks[(5 * 16 + z) * 16 + x] = BlockId.WATER;
                }
            return new ChunkData(position, blocks);
        }
    }
}
