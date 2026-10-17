package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.persistence.TestWorlds;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JunglePlantTest {
    @TempDir
    Path temporary;

    @Test
    void jungleMaterialsAndPlantStatesSurviveSaving() throws Exception {
        var world = new World();
        var pos = new ChunkPos(-1, 2, -1);
        world.putChunk(pos, new Chunk());
        for (int id = 150; id <= 165; id++) world.setBlock(new BlockPos(-16 + (id - 150), 34, -14), (byte) id, 64);
        var path = temporary.resolve("jungle.vxl");
        TestWorlds.save(path, 42, 37, world);
        var restored = new World();
        TestWorlds.restore(restored, TestWorlds.load(path), 64);
        restored.putChunk(pos, new Chunk());
        for (int id = 150; id <= 165; id++)
            assertEquals(
                    id,
                    restored.getBlock(new BlockPos(-16 + (id - 150), 34, -14)).orElseThrow());
    }

    @Test
    void loadingAnEmptyUpperSectionRemovesAnOrphanedLowerHalf() {
        var world = new World();
        var chunk = new Chunk();
        chunk.set(2, 14, 2, BlockId.GRASS);
        chunk.set(2, 15, 2, BlockId.LARGE_FERN);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        var edit = new WorldEdit(world, 64);
        edit.validatePlants(new ChunkPos(0, 0, 0));
        assertEquals(
                Byte.toUnsignedInt(BlockId.LARGE_FERN),
                world.getBlock(new BlockPos(2, 15, 2)).orElseThrow());
        world.putChunk(new ChunkPos(0, 1, 0), new Chunk());
        edit.validatePlants(new ChunkPos(0, 1, 0));
        assertEquals(0, world.getBlock(new BlockPos(2, 15, 2)).orElseThrow());
    }

    @Test
    void attachedPlantsWaitForSupportAndVineChainsFallWhenRemoved() {
        var world = new World();
        var plants = new Chunk();
        plants.set(15, 8, 2, BlockId.VINE_EAST);
        plants.set(15, 7, 2, BlockId.VINE_EAST);
        world.putChunk(new ChunkPos(0, 0, 0), plants);
        var edit = new WorldEdit(world, 64);
        edit.validatePlants(new ChunkPos(0, 0, 0));
        assertEquals(
                Byte.toUnsignedInt(BlockId.VINE_EAST),
                world.getBlock(new BlockPos(15, 8, 2)).orElseThrow());
        var support = new Chunk();
        support.set(0, 8, 2, BlockId.JUNGLE_LOG);
        world.putChunk(new ChunkPos(1, 0, 0), support);
        edit.validatePlants(new ChunkPos(1, 0, 0));
        edit.setBlock(new BlockPos(16, 8, 2), BlockId.AIR);
        assertEquals(0, world.getBlock(new BlockPos(15, 8, 2)).orElseThrow());
        assertEquals(0, world.getBlock(new BlockPos(15, 7, 2)).orElseThrow());
    }

    @Test
    void doublePlantsArePlacedAndRemovedAcrossSections() {
        var world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        world.putChunk(new ChunkPos(0, 1, 0), new Chunk());
        var edit = new WorldEdit(world, 64);
        edit.setBlock(new BlockPos(2, 14, 2), BlockId.GRASS);
        edit.setBlock(new BlockPos(2, 15, 2), BlockId.LARGE_FERN);
        assertEquals(
                Byte.toUnsignedInt(BlockId.LARGE_FERN_TOP),
                world.getBlock(new BlockPos(2, 16, 2)).orElseThrow());
        edit.setBlock(new BlockPos(2, 16, 2), BlockId.AIR);
        assertEquals(0, world.getBlock(new BlockPos(2, 15, 2)).orElseThrow());
    }
}
