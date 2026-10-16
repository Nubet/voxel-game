package com.norbertfila.persistence.world;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.persistence.TestWorlds;
import com.norbertfila.player.Inventory;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OreWorldPersistenceTest {
    @TempDir
    Path directory;

    @Test
    void oreEditsAndInventoryRoundTripAtBothBuildLimits() throws Exception {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        var world = new World();
        var inventory = new Inventory();
        byte[] ores = {BlockId.COAL_ORE, BlockId.IRON_ORE, BlockId.GOLD_ORE, BlockId.DIAMOND_ORE, BlockId.LAPIS_ORE};
        for (int i = 0; i < ores.length; i++) {
            world.setBlock(new BlockPos(i, WorldBounds.MIN_Y, -1), ores[i], 8);
            world.setBlock(new BlockPos(i, WorldBounds.MAX_Y_EXCLUSIVE - 1, -1), ores[i], 8);
            inventory.insert(ores[i], i + 1);
        }
        var snapshot = TestWorlds.capture(
                terrain.seed(),
                terrain.generatorVersion(),
                world,
                com.norbertfila.fluid.FluidSnapshot.EMPTY,
                inventory.snapshot());
        Path save = directory.resolve("ores.vxl");
        TestWorlds.save(save, snapshot);
        var decoded = TestWorlds.load(save);
        assertEquals(snapshot, decoded);
        var restored = new World();
        TestWorlds.restore(restored, decoded, 8);
        for (var change : world.changes().changes())
            assertEquals(change.value(), restored.getBlock(change.position()).orElseThrow());
    }

    @Test
    void currentCatalogRejectsOlderGeneratorsWithoutChangingTheirFiles() throws Exception {
        int currentVersion = new TerrainGenerator(42, TerrainConfig.defaults()).generatorVersion();
        for (int oldVersion : new int[] {24, 25}) {
            var metadata = new WorldMetadata(
                    new WorldIdentity(UUID.randomUUID(), 42, "default", oldVersion), "Old world", Instant.EPOCH);
            var oldCatalog = new WorldCatalog(directory, "default", oldVersion);
            oldCatalog.create(metadata);
            Path state = oldCatalog.open(metadata.identity().id()).statePath();
            byte[] before = Files.readAllBytes(state);
            var entry = new WorldCatalog(directory, "default", currentVersion)
                    .list().stream()
                            .filter(e -> e.id().equals(metadata.identity().id()))
                            .findFirst()
                            .orElseThrow();
            assertEquals(WorldEntry.Status.UNSUPPORTED_VERSION, entry.status());
            assertArrayEquals(before, Files.readAllBytes(state));
        }
    }
}
