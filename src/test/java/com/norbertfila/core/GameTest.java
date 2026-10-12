package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.player.SpawnResolver;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class GameTest {
    @Test
    void demandsTheWholeHeightRangeWithGroundAndHeadroom() {
        TerrainConfig config = TerrainConfig.defaults();
        var demand = Game.terrainDemand(config);
        assertTrue(demand.minChunkY() * Chunk.SIZE <= config.minHeight() - Chunk.SIZE);
        assertTrue((demand.maxChunkY() + 1) * Chunk.SIZE > config.maxHeight() + Chunk.SIZE);
        demand.update(new ChunkPos(0, 20, 0));
        assertEquals(
                81 * (demand.maxChunkY() - demand.minChunkY() + 1),
                demand.requested().size());
        assertTrue(demand.requested().contains(new ChunkPos(0, Math.floorDiv(config.maxHeight(), Chunk.SIZE), 0)));
    }

    @Test
    void perlinSpawnHasSolidFloorAndFreeBody() {
        TerrainGenerator generator = new TerrainGenerator(0, TerrainConfig.defaults());
        for (int x : new int[] {-17, 0, 31}) {
            var spawn = new SpawnResolver(generator).resolve(x, -1);
            int spawnX = (int) Math.floor(spawn.x()), spawnZ = (int) Math.floor(spawn.z());
            int floorY = (int) spawn.y() - 1;
            assertEquals(generator.surfaceHeight(spawnX, spawnZ) + 1, spawn.y());
            assertEquals(
                    generator.sample(spawnX, spawnZ).surfaceMaterial(),
                    generator
                            .generate(new ChunkPos(
                                    Math.floorDiv(spawnX, Chunk.SIZE),
                                    Math.floorDiv(floorY, Chunk.SIZE),
                                    Math.floorDiv(spawnZ, Chunk.SIZE)))
                            .get(
                                    Math.floorMod(spawnX, Chunk.SIZE),
                                    Math.floorMod(floorY, Chunk.SIZE),
                                    Math.floorMod(spawnZ, Chunk.SIZE)));
            assertFalse(generator.sample(spawnX, spawnZ).flooded());
            assertEquals(BlockId.AIR, generator.sample(spawnX, spawnZ).materialAt(floorY + 1));
            assertEquals(BlockId.AIR, generator.sample(spawnX, spawnZ).materialAt(floorY + 2));
        }
    }

    @Test
    void streamingIncludesWaterAboveTheTerrainBoundsWithHeadroom() {
        TerrainConfig c = TerrainConfig.defaults();
        TerrainConfig config = new TerrainConfig(
                c.baseHeight(), c.continental(), c.hills(), c.details(), c.mountains(), c.warp(), 350);
        var demand = Game.terrainDemand(config);
        assertTrue(((long) demand.maxChunkY() + 1) * Chunk.SIZE > config.waterLevel() + Chunk.SIZE);
    }
}
