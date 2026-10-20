package com.norbertfila.vegetation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.items.ItemState;
import com.norbertfila.persistence.world.*;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.world.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantGrowthPersistenceTest {
    @TempDir
    Path directory;

    @Test
    void plantingExploringSavingAndReturningMatchesContinuousGrowth() throws Exception {
        var world = new World();
        var origin = new ChunkPos(0, 0, 0);
        world.putChunk(origin, ground());
        var growth = new PlantGrowthSystem(world, 64, 42);
        world.setBlockChangeListener(growth::onBlockChanged);
        var edits = new WorldEdit(world, 64);
        var root = new BlockPos(2, 3, 2);
        edits.setBlock(root, BlockId.REED);
        growth.update(0, true);
        var planted = growth.snapshot();
        world.removeChunk(origin);
        growth.onChunkChanged(origin);
        assertEquals(0, growth.update(1_000_000, true).processed());
        var identity = new WorldIdentity(UUID.randomUUID(), 42, "default", 1);
        var snapshot = WorldSnapshots.capture(
                identity,
                1,
                world,
                new FluidSnapshot(1_000_000, List.of()),
                InventorySnapshot.EMPTY,
                new SavedPlayerState(1000, 3, 1000, 0, 0, PlayerController.Mode.SURVIVAL, false),
                ItemState.EMPTY,
                WorldTime.INITIAL,
                growth.snapshot());
        Path file = directory.resolve("farm.vxl");
        WorldStateCodec.write(file, snapshot);
        var saved = WorldStateCodec.read(file, identity);
        assertEquals(planted, saved.growth());

        var restoredWorld = new World();
        WorldSnapshots.restore(restoredWorld, saved, 64);
        var restored = new PlantGrowthSystem(restoredWorld, 64, 42);
        restored.restore(saved.growth(), saved.fluids().tick());
        restoredWorld.setBlockChangeListener(restored::onBlockChanged);
        assertEquals(0, restored.update(1_000_000, true).processed());
        assertEquals(0, restoredWorld.residentCount());
        restoredWorld.putChunk(origin, ground());
        new WorldEdit(restoredWorld, 64).validatePlants(origin);
        restored.onChunkChanged(origin);
        var caughtUp = restored.update(1_000_000, true);
        assertEquals(2, caughtUp.changedBlocks());
        assertTrue(caughtUp.dirtyChunks().contains(origin));
        assertEquals(BlockId.REED, restoredWorld.getBlock(new BlockPos(2, 5, 2)).orElseThrow());
        assertEquals(BlockId.AIR, restoredWorld.getBlock(new BlockPos(2, 6, 2)).orElseThrow());

        var referenceWorld = new World();
        referenceWorld.putChunk(origin, ground());
        var reference = new PlantGrowthSystem(referenceWorld, 64, 42);
        new WorldEdit(referenceWorld, 64).setBlock(root, BlockId.REED);
        reference.restore(planted, 0);
        reference.onChunkChanged(origin);
        for (int i = 0; i <= 100; i++) reference.update(i * 10000L, true);
        assertEquals(reference.snapshot(), restored.snapshot());
    }

    @Test
    void aPlantSavedImmediatelyAfterPlacementSurvivesEvictionBeforeTheNextUpdate() {
        var world = new World();
        var origin = new ChunkPos(0, 0, 0);
        world.putChunk(origin, ground());
        var growth = new PlantGrowthSystem(world, 64, 42);
        world.setBlockChangeListener(growth::onBlockChanged);
        growth.advanceClock(100);
        new WorldEdit(world, 64).setBlock(new BlockPos(2, 3, 2), BlockId.REED);
        world.removeChunk(origin);
        growth.onChunkChanged(origin);
        assertEquals(100, growth.snapshot().plants().getFirst().startedAt());
        assertEquals(0, growth.activeUpdates());
    }

    private static Chunk ground() {
        var chunk = new Chunk();
        chunk.set(2, 2, 2, BlockId.SAND);
        chunk.set(3, 2, 2, BlockId.WATER);
        return chunk;
    }
}
