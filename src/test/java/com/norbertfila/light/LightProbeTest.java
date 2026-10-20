package com.norbertfila.light;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class LightProbeTest {
    @Test
    void openSkyIsFullAndSealedRoomsAreDark() {
        var world = groundBelowZero();
        var probe = probe(world);
        assertEquals(15, probe.skyLight(new BlockPos(3, 0, 3)).orElseThrow());
        assertEquals(15, probe.skyLight(new BlockPos(3, 120, 3)).orElseThrow());
        carve(world, 0, -10, 0, 6, -8, 2);
        assertEquals(0, probe.skyLight(new BlockPos(3, -9, 1)).orElseThrow());
        assertEquals(0, probe.blockLight(new BlockPos(3, -9, 1)).orElseThrow());
    }

    @Test
    void skyLightFallsThroughAShaftAndFadesAlongTheTunnel() {
        var world = groundBelowZero();
        carve(world, 0, -10, 0, 6, -10, 0);
        carve(world, 0, -9, 0, 0, -1, 0);
        var probe = probe(world);
        assertEquals(15, probe.skyLight(new BlockPos(0, -10, 0)).orElseThrow());
        assertEquals(12, probe.skyLight(new BlockPos(3, -10, 0)).orElseThrow());
        assertEquals(9, probe.skyLight(new BlockPos(6, -10, 0)).orElseThrow());
    }

    @Test
    void roofEditsInvalidateTheCachedColumn() {
        var world = groundBelowZero();
        var sky = new SkyExposure(world);
        world.setBlockChangeListener(sky::onBlockChanged);
        var probe = new LightProbe(world, sky);
        assertEquals(15, probe.skyLight(new BlockPos(5, 0, 5)).orElseThrow());
        world.setBlock(new BlockPos(5, 20, 5), BlockId.STONE, 64);
        assertEquals(14, probe.skyLight(new BlockPos(5, 0, 5)).orElseThrow());
        assertEquals(20, sky.top(5, 5).orElseThrow());
        world.setBlock(new BlockPos(5, 20, 5), BlockId.GLASS, 64);
        assertEquals(15, probe.skyLight(new BlockPos(5, 0, 5)).orElseThrow());
    }

    @Test
    void torchLightDropsOneLevelPerStepAndStopsAtStone() {
        var world = groundBelowZero();
        carve(world, 0, -10, 0, 8, -10, 0);
        carve(world, 0, -10, 4, 8, -10, 4);
        world.setBlock(new BlockPos(0, -10, 0), BlockId.TORCH, 64);
        var probe = probe(world);
        assertEquals(14, probe.blockLight(new BlockPos(0, -10, 0)).orElseThrow());
        assertEquals(13, probe.blockLight(new BlockPos(1, -10, 0)).orElseThrow());
        assertEquals(6, probe.blockLight(new BlockPos(8, -10, 0)).orElseThrow());
        assertEquals(0, probe.blockLight(new BlockPos(0, -10, 4)).orElseThrow());
        carve(world, 8, -10, 1, 8, -10, 3);
        assertEquals(1, probe.blockLight(new BlockPos(7, -10, 4)).orElseThrow());
        assertEquals(0, probe.blockLight(new BlockPos(0, -10, 4)).orElseThrow());
    }

    @Test
    void lavaGlowsAtFullStrength() {
        var world = groundBelowZero();
        carve(world, 0, -10, 0, 4, -10, 0);
        world.setBlock(new BlockPos(0, -10, 0), BlockId.LAVA, 64);
        assertEquals(13, probe(world).blockLight(new BlockPos(2, -10, 0)).orElseThrow());
    }

    @Test
    void missingChunksMakeTheAnswerUnknown() {
        var world = groundBelowZero();
        var probe = probe(world);
        world.removeChunk(new ChunkPos(1, 0, 0));
        assertEquals(OptionalInt.empty(), probe.blockLight(new BlockPos(14, 0, 3)));
        world.removeChunk(new ChunkPos(0, WorldBounds.MAX_CHUNK_Y, 0));
        assertEquals(OptionalInt.empty(), probe.skyLight(new BlockPos(3, 0, 3)));
    }

    @Test
    void torchesEmitLightButDoNotBlockIt() {
        assertEquals(14, BlockRegistry.DEFAULT.lightEmission(BlockId.TORCH));
        assertEquals(15, BlockRegistry.DEFAULT.lightEmission(BlockId.LAVA));
        assertEquals(0, BlockRegistry.DEFAULT.lightEmission(BlockId.STONE));
        assertTrue(BlockRegistry.DEFAULT.transmitsLight(BlockId.TORCH));
        assertFalse(BlockRegistry.DEFAULT.transmitsLight(BlockId.STONE));
        assertFalse(BlockRegistry.DEFAULT.isCollidable(BlockId.TORCH));
        assertFalse(BlockRegistry.DEFAULT.definition(BlockId.TORCH).replaceable());
    }

    @Test
    void torchesNeedAFullBlockBelow() {
        var world = groundBelowZero();
        assertTrue(PlantRules.canPlace(world, new BlockPos(3, 0, 3), BlockId.TORCH));
        assertFalse(PlantRules.canPlace(world, new BlockPos(3, 5, 3), BlockId.TORCH));
        world.setBlock(new BlockPos(3, 0, 3), BlockId.LEAVES, 64);
        assertFalse(PlantRules.canPlace(world, new BlockPos(3, 1, 3), BlockId.TORCH));
    }

    static World groundBelowZero() {
        var world = new World();
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                for (int y = WorldBounds.MIN_CHUNK_Y; y <= WorldBounds.MAX_CHUNK_Y; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
        return world;
    }

    static void carve(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) world.setBlock(new BlockPos(x, y, z), BlockId.AIR, 64);
    }

    private static LightProbe probe(World world) {
        var sky = new SkyExposure(world);
        world.setBlockChangeListener(sky::onBlockChanged);
        return new LightProbe(world, sky);
    }
}
