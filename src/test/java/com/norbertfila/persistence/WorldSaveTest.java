package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldSaveTest {
    @TempDir
    Path temporaryDirectory;

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
