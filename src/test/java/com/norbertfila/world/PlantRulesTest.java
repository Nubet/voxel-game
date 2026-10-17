package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.persistence.TestWorlds;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantRulesTest {
    @TempDir
    Path temporary;

    @Test
    void snowSurvivesOnLeavesUntilItsSupportIsRemovedAcrossASectionBoundary() {
        for (byte leaves : new byte[] {BlockId.LEAVES, BlockId.PINE_NEEDLES, BlockId.SWAMP_LEAVES}) {
            var world = new World();
            var lower = new Chunk();
            lower.set(2, 15, 2, leaves);
            var upper = new Chunk();
            upper.set(2, 0, 2, BlockId.SNOW_LAYER);
            world.putChunk(new ChunkPos(0, 0, 0), lower);
            world.putChunk(new ChunkPos(0, 1, 0), upper);
            var edit = new WorldEdit(world, 64);
            edit.validatePlants(new ChunkPos(0, 1, 0));
            var snow = new BlockPos(2, 16, 2);
            assertEquals(BlockId.SNOW_LAYER, world.getBlock(snow).orElseThrow());
            edit.setBlock(new BlockPos(2, 15, 2), BlockId.AIR);
            assertEquals(BlockId.AIR, world.getBlock(snow).orElseThrow());
        }
    }

    @Test
    void reedsKeepUnknownWaterNeighborsAndRevalidateAcrossHorizontalAndVerticalSeams() {
        for (boolean waterPresent : new boolean[] {false, true}) {
            var world = new World();
            var lower = new ChunkPos(0, 0, 0);
            var upper = new ChunkPos(0, 1, 0);
            var neighbor = new ChunkPos(1, 0, 0);
            var ground = new Chunk();
            ground.set(15, 15, 2, BlockId.MUD);
            var plants = new Chunk();
            plants.set(15, 0, 2, BlockId.REED);
            world.putChunk(lower, ground);
            world.putChunk(upper, plants);
            var edit = new WorldEdit(world, 64);
            edit.validatePlants(upper);
            var reed = new BlockPos(15, 16, 2);
            assertEquals(BlockId.REED, world.getBlock(reed).orElseThrow());
            var water = new Chunk();
            if (waterPresent) water.set(0, 15, 2, BlockId.WATER);
            world.putChunk(neighbor, water);
            edit.validatePlants(neighbor);
            assertEquals(
                    waterPresent ? BlockId.REED : BlockId.AIR,
                    world.getBlock(reed).orElseThrow());
            if (waterPresent) {
                assertTrue(PlantRules.canPlace(world, reed, BlockId.REED));
                edit.setBlock(new BlockPos(16, 15, 2), BlockId.AIR);
                assertEquals(BlockId.AIR, world.getBlock(reed).orElseThrow());
            }
        }
    }

    @Test
    void removingSupportAcrossNegativeVerticalSeamInvalidatesBothSectionsAndPersists() throws Exception {
        World world = new World();
        ChunkPos lower = new ChunkPos(-1, -1, -1), upper = new ChunkPos(-1, 0, -1);
        world.putChunk(lower, new Chunk());
        world.putChunk(upper, new Chunk());
        BlockPos ground = new BlockPos(-2, -1, -2), plant = new BlockPos(-2, 0, -2);
        WorldEdit edit = new WorldEdit(world, 64);
        edit.setBlock(ground, BlockId.GRASS);
        edit.setBlock(plant, BlockId.DANDELION_WHITE);
        assertTrue(PlantRules.canPlace(world, plant));
        var result = edit.setBlock(ground, BlockId.AIR);
        assertEquals(BlockId.AIR, world.getBlock(plant).orElseThrow());
        assertTrue(result.dirtyChunks().containsAll(java.util.List.of(lower, upper)));
        Path save = temporary.resolve("vegetation.vxl");
        TestWorlds.save(save, 42, 11, world);
        World restored = new World();
        TestWorlds.restore(restored, TestWorlds.load(save), 64);
        Chunk regenerated = new Chunk();
        regenerated.set(14, 0, 14, BlockId.DANDELION_WHITE);
        restored.putChunk(upper, regenerated);
        assertEquals(BlockId.AIR, restored.getBlock(plant).orElseThrow());
    }

    @Test
    void unloadedSupportIsNotAirAndIsRecheckedInEitherAdmissionOrder() {
        for (boolean topFirst : new boolean[] {true, false}) {
            World world = new World();
            WorldEdit edit = new WorldEdit(world, 64);
            ChunkPos lower = new ChunkPos(0, 0, 0), upper = new ChunkPos(0, 1, 0);
            Chunk top = new Chunk();
            top.set(2, 0, 2, BlockId.FERN);
            BlockPos plant = new BlockPos(2, 16, 2);
            if (topFirst) {
                world.putChunk(upper, top);
                assertTrue(edit.validatePlants(upper).isEmpty());
                assertEquals(BlockId.FERN, world.getBlock(plant).orElseThrow());
                world.putChunk(lower, new Chunk());
                assertFalse(edit.validatePlants(lower).isEmpty());
            } else {
                world.putChunk(lower, new Chunk());
                world.putChunk(upper, top);
                assertFalse(edit.validatePlants(upper).isEmpty());
            }
            assertEquals(BlockId.AIR, world.getBlock(plant).orElseThrow());
        }
    }

    @Test
    void fivePlantsSeparateCollisionShapeAndReplacementFromExistingBlocks() {
        for (byte plant : new byte[] {
            BlockId.SHORT_GRASS, BlockId.FERN, BlockId.DANDELION_YELLOW, BlockId.DANDELION_WHITE, BlockId.POPPY
        }) {
            var definition = BlockRegistry.DEFAULT.definition(plant);
            assertTrue(BlockId.isValid(plant));
            assertTrue(PlantRules.isPlant(plant));
            assertFalse(definition.collidable());
            assertFalse(definition.occludesFullFace());
            assertTrue(definition.replaceable());
            assertTrue(definition.breakable());
            assertTrue(definition.targetable());
            assertEquals(BlockShape.CROSS, definition.shape());
            assertEquals(RenderLayer.CUTOUT, definition.renderLayer());
        }
        assertFalse(BlockRegistry.DEFAULT.definition(BlockId.LEAVES).replaceable());
        assertEquals(
                BlockShape.CUBE,
                BlockRegistry.DEFAULT.definition(BlockId.LEAVES).shape());
    }
}
