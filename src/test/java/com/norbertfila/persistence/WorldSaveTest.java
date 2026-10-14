package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldSaveTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void schemaOneWaterLoadsAsSourceWithNoFluidTasks() throws Exception {
        Path save = temporaryDirectory.resolve("legacy.vxl");
        try (var out = new DataOutputStream(Files.newOutputStream(save))) {
            out.writeInt(0x56584C31);
            out.writeInt(1);
            out.writeLong(42);
            out.writeInt(7);
            out.writeInt(1);
            out.writeInt(-2);
            out.writeInt(-1);
            out.writeInt(-1);
            out.writeInt(1);
            out.writeInt(-17);
            out.writeInt(-1);
            out.writeInt(-9);
            out.writeByte(BlockId.WATER);
        }
        var loaded = WorldSave.load(save);
        assertEquals(1, loaded.schemaVersion());
        assertEquals(FluidSnapshot.EMPTY, loaded.fluids());
        World world = new World();
        WorldSave.restore(world, loaded, 8);
        assertEquals(
                WaterState.SOURCE,
                world.getWaterState(new BlockPos(-17, -1, -9)).orElseThrow());
    }

    @Test
    void allWaterStatesAndBothTaskKindsSurviveDiskAndRegeneration() throws Exception {
        World world = new World();
        for (byte state = 0; state <= 8; state++)
            world.setBlock(new BlockPos(-16 + state, 1, 1), BlockId.WATER, state, 8);
        var fluids = new FluidSnapshot(
                500,
                List.of(
                        new FluidSnapshot.Update(new BlockPos(-16, 1, 1), 3, List.of()),
                        new FluidSnapshot.Update(new BlockPos(-15, 1, 1), 15, List.of(new ChunkPos(-2, 0, 0)))));
        var snapshot = WorldSave.capture(42, 7, world, fluids);
        Path save = temporaryDirectory.resolve("states.vxl");
        WorldSave.save(save, snapshot);
        World restored = new World();
        var loaded = WorldSave.load(save);
        assertEquals(2, loaded.schemaVersion());
        assertEquals(fluids, loaded.fluids());
        WorldSave.restore(restored, loaded, 8);
        restored.putChunk(new ChunkPos(-1, 0, 0), new Chunk());
        for (byte state = 0; state <= 8; state++)
            assertEquals(
                    state,
                    restored.getWaterState(new BlockPos(-16 + state, 1, 1)).orElseThrow());
        byte[] bytes = Files.readAllBytes(save);
        bytes[53] = 9;
        Files.write(save, bytes);
        assertThrows(IOException.class, () -> WorldSave.load(save));
    }

    @Test
    void rejectsMalformedFluidDelaysCountsAndDuplicateTasks() throws Exception {
        Path save = temporaryDirectory.resolve("bad-fluid.vxl");
        var task = new FluidSnapshot.Update(new BlockPos(1, 1, 1), 2, List.of());
        assertThrows(IllegalArgumentException.class, () -> new FluidSnapshot(0, List.of(task, task)));
        WorldSave.save(save, WorldSave.capture(1, 1, new World(), new FluidSnapshot(10, List.of(task))));
        byte[] bytes = Files.readAllBytes(save);
        byte[] invalidDelay = bytes.clone();
        java.nio.ByteBuffer.wrap(invalidDelay).putLong(48, -1);
        Files.write(save, invalidDelay);
        assertThrows(IOException.class, () -> WorldSave.load(save));
        byte[] invalidCount = bytes.clone();
        java.nio.ByteBuffer.wrap(invalidCount).putInt(32, Integer.MAX_VALUE);
        Files.write(save, invalidCount);
        assertThrows(IOException.class, () -> WorldSave.load(save));
        Files.write(save, Arrays.copyOf(bytes, bytes.length - 1));
        assertThrows(IOException.class, () -> WorldSave.load(save));
    }

    @Test
    void fallingStateSurvivesReplacingAnExistingSourceSave() throws Exception {
        World world = new World();
        BlockPos position = new BlockPos(-17, -1, -9);
        world.setBlock(position, BlockId.WATER, 8);
        Path save = temporaryDirectory.resolve("water-state.vxl");
        WorldSave.save(save, 42, 7, world);
        world.setBlock(position, BlockId.WATER, WaterState.FALLING, 8);

        WorldSave.save(save, 42, 7, world);
        World restored = new World();
        WorldSave.restore(restored, WorldSave.load(save), 8);
        assertEquals(WaterState.FALLING, restored.getWaterState(position).orElseThrow());
    }

    @Test
    void restoredDeltasReachChunkSnapshotsOnFirstLoad() throws Exception {
        HillsGenerator generator = new HillsGenerator();
        ChunkPos position = new ChunkPos(-1, 0, -1);
        World world = new World();
        world.putChunk(position, Chunk.fromBlocks(generator.generate(position).blocks()));
        WorldEdit edits = new WorldEdit(world, 8);
        BlockPos removed = new BlockPos(-1, 4, -9);
        BlockPos placed = new BlockPos(-1, 15, -9);
        assertTrue(edits.setBlock(removed, BlockId.AIR).changed());
        assertTrue(edits.setBlock(placed, BlockId.STONE).changed());
        Path save = temporaryDirectory.resolve("edited.vxl");
        WorldSave.save(save, generator.seed(), generator.generatorVersion(), world);
        World restored = new World();
        WorldSave.restore(restored, WorldSave.load(save), 8);
        restored.changes().drainDirtyTiles();

        restored.putChunk(
                position, Chunk.fromBlocks(generator.generate(position).blocks()));

        var snapshot = restored.getChunk(position).snapshot();
        BlockPos localRemoved = Coordinates.worldToLocal(removed);
        BlockPos localPlaced = Coordinates.worldToLocal(placed);
        assertEquals(BlockId.AIR, snapshot.get(localRemoved.x(), localRemoved.y(), localRemoved.z()));
        assertEquals(BlockId.STONE, snapshot.get(localPlaced.x(), localPlaced.y(), localPlaced.z()));
        assertEquals(2, snapshot.revision());
        assertEquals(world.changes().changes(), restored.changes().changes());
        assertTrue(restored.changes().dirtyTiles().isEmpty());
    }

    @Test
    void roundTripsSeedVersionAndOnlyOverlayDeltas() throws Exception {
        World world = new World();
        world.setBlock(new BlockPos(-1, 4, -9), BlockId.STONE, 8);
        world.setBlock(new BlockPos(16, 1, 16), BlockId.GRASS, 8);
        Path save = temporaryDirectory.resolve("world.vxl");

        WorldSave.save(save, 42L, 7, world);
        WorldSave.Loaded loaded = WorldSave.load(save);
        World restored = new World();
        WorldSave.restore(restored, loaded, 8);

        assertEquals(42L, loaded.seed());
        assertEquals(7, loaded.generatorVersion());
        assertEquals(2, loaded.deltas().size());
        assertEquals(BlockId.STONE, restored.getBlock(new BlockPos(-1, 4, -9)).orElseThrow());
        assertEquals(BlockId.GRASS, restored.getBlock(new BlockPos(16, 1, 16)).orElseThrow());
    }

    @Test
    void removedProceduralWaterStaysAirAfterSavingAndRegeneration() throws Exception {
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        TerrainConfig c = new TerrainConfig(15, disabled, disabled, disabled);
        TerrainGenerator generator = new TerrainGenerator(
                0, new TerrainConfig(15, disabled, disabled, disabled, c.mountains(), c.warp(), 17), (x, z) -> 0);
        ChunkPos position = new ChunkPos(-1, 1, -1);
        BlockPos removed = new BlockPos(-1, 16, -1);
        World world = new World();
        world.putChunk(position, Chunk.fromBlocks(generator.generate(position).blocks()));
        assertEquals(BlockId.WATER, world.getBlock(removed).orElseThrow());
        assertTrue(new WorldEdit(world, 8).setBlock(removed, BlockId.AIR).changed());
        Path save = temporaryDirectory.resolve("procedural-water.vxl");
        WorldSave.save(save, generator.seed(), generator.generatorVersion(), world);
        var loaded = WorldSave.load(save);
        assertEquals(11, loaded.generatorVersion());
        World restored = new World();
        WorldSave.restore(restored, loaded, 8);
        restored.putChunk(
                position, Chunk.fromBlocks(generator.generate(position).blocks()));
        assertEquals(BlockId.AIR, restored.getBlock(removed).orElseThrow());
        assertEquals(BlockId.WATER, restored.getBlock(new BlockPos(-2, 16, -1)).orElseThrow());
    }

    @Test
    void roundTripsWaterDeltaAtNegativeCoordinates() throws Exception {
        World world = new World();
        BlockPos position = new BlockPos(-17, -1, -9);
        world.setBlock(position, BlockId.WATER, 8);
        Path save = temporaryDirectory.resolve("water.vxl");
        WorldSave.save(save, 42, 7, world);
        World restored = new World();
        WorldSave.restore(restored, WorldSave.load(save), 8);
        restored.putChunk(Coordinates.worldToChunk(position), new Chunk());
        assertEquals(BlockId.WATER, restored.getBlock(position).orElseThrow());
        assertFalse(restored.isSolid(position));
    }

    @Test
    void savesDeltasInStableByteOrder() throws Exception {
        World world = new World();
        world.setBlock(new BlockPos(4, 2, 3), BlockId.STONE, 8);
        world.setBlock(new BlockPos(-1, 2, 3), BlockId.DIRT, 8);
        Path first = temporaryDirectory.resolve("first.vxl");
        Path second = temporaryDirectory.resolve("second.vxl");

        WorldSave.save(first, 1L, 1, world);
        WorldSave.save(second, 1L, 1, world);

        assertTrue(Arrays.equals(Files.readAllBytes(first), Files.readAllBytes(second)));
    }

    @Test
    void rejectsCorruptedHeaderAndTruncatedData() throws Exception {
        Path save = temporaryDirectory.resolve("bad.vxl");
        Files.write(save, new byte[] {0, 1, 2});

        assertThrows(IOException.class, () -> WorldSave.load(save));
    }

    @Test
    void invalidSaveArgumentsDoNotReplaceExistingTarget() throws Exception {
        Path save = temporaryDirectory.resolve("world.vxl");
        Files.writeString(save, "old");

        assertThrows(IllegalArgumentException.class, () -> WorldSave.save(save, 1L, -1, new World()));

        assertEquals("old", Files.readString(save));
    }
}
