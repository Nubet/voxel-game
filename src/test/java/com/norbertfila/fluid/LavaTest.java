package com.norbertfila.fluid;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.persistence.TestWorlds;
import com.norbertfila.world.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LavaTest {
    @TempDir
    Path directory;

    @Test
    void lavaFlowsAcrossChunksAndRetainsItsTypeAfterRestart() throws Exception {
        World first = floorWorld();
        var source = new BlockPos(15, 1, 8);
        first.setBlock(source, BlockId.LAVA, 8);
        var original = new FluidSimulator(first, 8, 12);
        original.onBlockChanged(source, 0);
        for (int tick = 1; tick <= 20; tick++) original.tick(tick);
        Path file = directory.resolve("lava.vxl");
        TestWorlds.save(file, TestWorlds.capture(42, 44, first, original.snapshot(20)));
        var loaded = TestWorlds.load(file);
        World second = floorWorld();
        TestWorlds.restore(second, loaded, 8);
        var resumed = new FluidSimulator(second, 8, 12);
        resumed.restore(loaded.fluids());
        for (int tick = 21; tick <= 300; tick++) {
            assertEquals(original.tick(tick), resumed.tick(tick));
            assertEquals(first.changes().changes(), second.changes().changes());
        }
        assertEquals(
                Byte.toUnsignedInt(BlockId.LAVA),
                first.getBlock(new BlockPos(16, 1, 8)).orElseThrow());
        assertEquals(1, first.getWaterState(new BlockPos(16, 1, 8)).orElseThrow());
        first.setBlock(source, BlockId.AIR, 8);
        original.onBlockChanged(source, 300);
        for (int tick = 301; tick <= 600; tick++) original.tick(tick);
        assertEquals(BlockId.AIR, first.getBlock(new BlockPos(16, 1, 8)).orElseThrow());
    }

    @Test
    void fluidsNeverUseEachOthersSourcesAndResolveCompetingDonors() {
        World world = floorWorld();
        var target = new BlockPos(8, 1, 8);
        world.setBlock(new BlockPos(7, 1, 8), BlockId.LAVA, 8);
        world.setBlock(new BlockPos(9, 1, 8), BlockId.WATER, 8);
        assertEquals(BlockId.WATER, FluidFlowRules.fluidAt(world, target));
        world.setBlock(target, BlockId.LAVA, WaterState.flowing(4), 8);
        assertEquals(BlockId.LAVA, FluidFlowRules.fluidAt(world, target));
        world.setBlock(new BlockPos(7, 1, 8), BlockId.AIR, 8);
        assertEquals(WaterState.EMPTY, FluidFlowRules.nextState(world, target, new java.util.HashSet<>()));
        world.setBlock(target, BlockId.AIR, 8);
        world.setBlock(new BlockPos(7, 1, 8), BlockId.LAVA, 8);
        world.setBlock(new BlockPos(9, 1, 8), BlockId.WATER, WaterState.flowing(7), 8);
        var update = FluidFlowRules.nextUpdate(world, target, new java.util.HashSet<>(), false);
        assertEquals(BlockId.LAVA, update.fluid());
        assertEquals(1, update.state());
    }

    @Test
    void lavaFallsAndMetadataSurvivesSnapshotsAndPackedDeltas() {
        World world = floorWorld();
        var source = new BlockPos(8, 5, 8);
        world.setBlock(source, BlockId.LAVA, 8);
        var simulator = new FluidSimulator(world, 8);
        simulator.onBlockChanged(source, 0);
        for (int tick = 1; tick <= 30; tick++) simulator.tick(tick);
        assertEquals(
                WaterState.FALLING, world.getWaterState(new BlockPos(8, 4, 8)).orElseThrow());
        assertTrue(world.getChunk(new ChunkPos(0, 0, 0)).containsWater());
        var snapshot = world.getChunk(new ChunkPos(0, 0, 0)).snapshot();
        assertEquals(WaterState.FALLING, snapshot.waterState(8, 4, 8));
        var delta = new ChunkDelta(
                new ChunkPos(0, 0, 0),
                List.of(new ChunkDelta.BlockChange(new BlockPos(8, 4, 8), BlockId.LAVA, WaterState.FALLING)));
        assertEquals(BlockId.LAVA, delta.value(0));
        assertEquals(WaterState.FALLING, delta.waterState(0));
    }

    @Test
    void lavaUsesOpaqueFluidGeometryAndDoesNotShareWaterHeights() {
        var chunk = new Chunk();
        chunk.set(8, 1, 8, BlockId.LAVA);
        chunk.set(9, 1, 8, BlockId.WATER, WaterState.flowing(7));
        var snapshot = chunk.snapshot();
        var neighbors = BlockNeighborhood.centerOnly(snapshot);
        var mesh = new WaterMesher().mesh(snapshot, neighbors, 0, 0, 0, ChunkMesh.ALL);
        assertEquals(0, mesh.solid().quadCount());
        assertEquals(6, mesh.water().ranges().opaque());
        assertEquals(6, mesh.water().ranges().translucent());
        assertEquals(WaterSurface.SOLID, neighbors.fluidSample(9, 1, 8, BlockId.LAVA));
        var world = floorWorld();
        world.setBlock(new BlockPos(8, 1, 8), BlockId.LAVA, 8);
        var contact = com.norbertfila.player.WaterContact.sample(
                world,
                new Aabb(8.2, 1, 8.2, 8.8, 2.8, 8.8),
                new com.norbertfila.player.PlayerState.Position(8.5, 1.5, 8.5));
        assertTrue(contact.submergedFraction() > 0);
        assertFalse(contact.eyesSubmerged());
        assertTrue(new com.norbertfila.items.BlockDropResolver()
                .getDrops(Byte.toUnsignedInt(BlockId.LAVA), com.norbertfila.items.ToolContext.HAND)
                .isEmpty());
    }

    private static World floorWorld() {
        var world = new World();
        for (int cx = 0; cx <= 1; cx++) {
            var chunk = new Chunk();
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) chunk.set(x, 0, z, BlockId.STONE);
            world.putChunk(new ChunkPos(cx, 0, 0), chunk);
        }
        return world;
    }
}
