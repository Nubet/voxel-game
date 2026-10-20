package com.norbertfila.weather;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class PrecipitationFieldTest {
    @Test
    void roofsLeavesGlassSnowAndWaterStopPrecipitationButPlantsDoNot() {
        var world = column();
        var field = new PrecipitationField(world, new BiomeWeather(new HillsGenerator()), 2);
        world.setBlockChangeListener(field::onBlockChanged);
        for (byte block :
                new byte[] {BlockId.STONE, BlockId.GLASS, BlockId.LEAVES, BlockId.WATER, BlockId.SNOW_LAYER}) {
            world.setBlock(new BlockPos(2, 10, 2), block, 64);
            field.update(2, 2, true);
            assertEquals(10 + BlockRegistry.DEFAULT.definition(block).model().maxY(), surface(field));
        }
        world.setBlock(new BlockPos(2, 10, 2), BlockId.SHORT_GRASS, 64);
        field.update(2, 2, true);
        assertEquals(1, surface(field));
        var old = field.snapshot();
        world.setBlock(new BlockPos(2, 50, 2), BlockId.PLANKS, 64);
        field.update(2, 2, true);
        assertEquals(51, surface(field));
        assertEquals(
                1,
                old.stream()
                        .filter(c -> c.x() == 2 && c.z() == 2)
                        .findFirst()
                        .orElseThrow()
                        .surface());
    }

    @Test
    void unknownSectionsSuppressRainAndReloadRestoresEditedRoofs() {
        var world = column();
        var field = new PrecipitationField(world, new BiomeWeather(new HillsGenerator()), 2);
        world.setBlockChangeListener(field::onBlockChanged);
        world.setBlock(new BlockPos(2, 50, 2), BlockId.GLASS, 64);
        field.update(2, 2, true);
        var missing = new ChunkPos(0, WorldBounds.MAX_CHUNK_Y, 0);
        world.removeChunk(missing);
        field.onChunkChanged(missing);
        field.update(2, 2, true);
        assertTrue(field.snapshot().isEmpty());
        world.putChunk(missing, new Chunk());
        field.onChunkChanged(missing);
        field.update(2, 2, true);
        assertEquals(51, surface(field));
        field.update(-1000, -1000, true);
        assertTrue(field.snapshot().isEmpty());
    }

    @Test
    void updatesAreBoundedAndDryWeatherDoesNotScanColumns() {
        var field = new PrecipitationField(column(), new BiomeWeather(new HillsGenerator()));
        assertEquals(0, field.update(2, 2, false));
        assertTrue(field.snapshot().isEmpty());
        assertEquals(PrecipitationField.BUDGET, field.update(2, 2, true));
        for (int i = 0; i < 40; i++) assertTrue(field.update(2, 2, true) <= PrecipitationField.BUDGET);
        assertEquals(0, field.update(2, 2, true));
        var snapshot = field.snapshot();
        field.update(2, 2, true);
        assertSame(snapshot, field.snapshot());
    }

    private static float surface(PrecipitationField field) {
        return field.snapshot().stream()
                .filter(c -> c.x() == 2 && c.z() == 2)
                .findFirst()
                .orElseThrow()
                .surface();
    }

    private static World column() {
        var world = new World();
        for (int y = WorldBounds.MIN_CHUNK_Y; y <= WorldBounds.MAX_CHUNK_Y; y++)
            world.putChunk(new ChunkPos(0, y, 0), new Chunk());
        world.setBlock(new BlockPos(2, 0, 2), BlockId.STONE, 64);
        return world;
    }
}
