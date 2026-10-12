package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class WaterSpawnTest {
    @Test
    void findsDeterministicDryClearSpawnsNearFloodedCoordinatesForSeveralSeeds() {
        long[] seeds = {0, 42, -7, 1234};
        int[][] wet = {{-48, -144}, {-16, -32}, {-32, 80}, {32, 112}};
        for (int i = 0; i < seeds.length; i++) {
            TerrainGenerator terrain = new TerrainGenerator(seeds[i], TerrainConfig.defaults());
            assertTrue(terrain.sample(wet[i][0], wet[i][1]).flooded());
            SpawnResolver resolver = new SpawnResolver(terrain);
            var spawn = resolver.resolve(wet[i][0], wet[i][1]);
            assertEquals(spawn, resolver.resolve(wet[i][0], wet[i][1]));
            int x = (int) Math.floor(spawn.x()), z = (int) Math.floor(spawn.z());
            assertFalse(terrain.sample(x, z).flooded());
            assertTrue(Math.abs((long) x - wet[i][0]) <= 1024 && Math.abs((long) z - wet[i][1]) <= 1024);
            int feetY = (int) spawn.y();
            assertTrue(BlockRegistry.DEFAULT.isCollidable(block(terrain, x, feetY - 1, z)));
            assertEquals(BlockId.AIR, block(terrain, x, feetY, z));
            assertEquals(BlockId.AIR, block(terrain, x, feetY + 1, z));
        }
    }

    @Test
    void groundAtTheWaterLevelIsDryButEntirelyFloodedTerrainFailsBoundedly() {
        TerrainSource dry = flat(8, 8);
        assertEquals(new PlayerState.Position(.5, 9, .5), new SpawnResolver(dry).resolve(0, 0));
        assertThrows(IllegalStateException.class, () -> new SpawnResolver(flat(8, 16)).resolve(0, 0));
    }

    @Test
    void rejectsActualCanopyVoxelsEvenWhenHeightSamplesAreDry() {
        TerrainSource terrain = flat(8, 8);
        TerrainSource canopy = new TerrainSource() {
            public long seed() {
                return terrain.seed();
            }

            public int generatorVersion() {
                return terrain.generatorVersion();
            }

            public int surfaceHeight(int x, int z) {
                return terrain.surfaceHeight(x, z);
            }

            public TerrainSummary sample(int x, int z) {
                return terrain.sample(x, z);
            }

            public com.norbertfila.generation.ChunkData generate(ChunkPos position) {
                byte[] blocks = terrain.generate(position).blocks();
                if (position.equals(new ChunkPos(0, 0, 0))) blocks[9 * Chunk.SIZE * Chunk.SIZE] = BlockId.LEAVES;
                return new com.norbertfila.generation.ChunkData(position, blocks);
            }
        };
        var spawn = new SpawnResolver(canopy).resolve(0, 0);
        assertNotEquals(.5, spawn.x());
        assertEquals(BlockId.AIR, block(canopy, (int) Math.floor(spawn.x()), 9, (int) Math.floor(spawn.z())));
    }

    private static TerrainGenerator flat(int height, int level) {
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        TerrainConfig c = new TerrainConfig(height, disabled, disabled, disabled);
        return new TerrainGenerator(
                0,
                new TerrainConfig(height, disabled, disabled, disabled, c.mountains(), c.warp(), level),
                (x, z) -> 0);
    }

    private static byte block(TerrainSource source, int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        BlockPos local = Coordinates.worldToLocal(position);
        return source.generate(Coordinates.worldToChunk(position)).get(local.x(), local.y(), local.z());
    }
}
