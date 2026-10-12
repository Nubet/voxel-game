package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.norbertfila.fluid.FluidSnapshot;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveSchedulerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void coalescesThousandEditsAndPersistsLatestRevision() throws Exception {
        World world = new World();
        Path save = temporaryDirectory.resolve("world.vxl");
        try (SaveScheduler scheduler = new SaveScheduler(save, 10)) {
            for (int index = 0; index < 1_000; index++) {
                world.setBlock(new BlockPos(index, 0, 0), BlockId.STONE, 8);
                scheduler.request(WorldSave.capture(7, 1, world), index);
            }
            scheduler.awaitIdle();

            assertEquals(999, scheduler.dirtyRevision());
            assertEquals(999, scheduler.savedRevision());
            assertEquals(1_000, countChanges(WorldSave.load(save)));
        }
    }

    @Test
    void closeFlushesPendingSnapshot() throws Exception {
        World world = new World();
        Path save = temporaryDirectory.resolve("world.vxl");
        SaveScheduler scheduler = new SaveScheduler(save, 10_000);
        world.setBlock(new BlockPos(-1, 0, -1), BlockId.GRASS, 8);
        scheduler.request(WorldSave.capture(9, 2, world), 1);

        scheduler.close();

        WorldSave.Loaded loaded = WorldSave.load(save);
        assertEquals(9, loaded.seed());
        assertNotNull(loaded.deltas());
        assertEquals(1, countChanges(loaded));
    }

    @Test
    void workerPersistsCapturedWaterAndTasksRatherThanLaterWorldEdits() throws Exception {
        World world = new World();
        BlockPos position = new BlockPos(-1, 1, -1);
        world.setBlock(position, BlockId.WATER, WaterState.FALLING, 8);
        var fluids = new FluidSnapshot(10, List.of(new FluidSnapshot.Update(position, 5, List.of())));
        Path save = temporaryDirectory.resolve("fluid.vxl");
        try (SaveScheduler scheduler = new SaveScheduler(save, 10_000)) {
            scheduler.request(WorldSave.capture(9, 2, world, fluids), 1);
            world.setBlock(position, BlockId.AIR, 8);
        }
        var loaded = WorldSave.load(save);
        assertEquals(fluids, loaded.fluids());
        assertEquals(
                WaterState.FALLING,
                loaded.deltas().getFirst().changes().getFirst().waterState());
    }

    private static int countChanges(WorldSave.Loaded save) {
        return save.deltas().stream().mapToInt(delta -> delta.changes().size()).sum();
    }
}
