package com.norbertfila.interaction;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BlockBreakingTest {
    private final World world = new World();
    private final BlockPos position = new BlockPos(3, 1, 1);
    private final BlockBreaking breaking = new BlockBreaking(world);

    @Test
    void survivalRequiresAFullSecondOfContinuousHoldingWithoutEditingTheWorld() {
        var hit = hit(position, BlockId.STONE);
        for (int tick = 0; tick < FixedSimulation.TICKS_PER_SECOND - 1; tick++)
            assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, tick == 0, FixedSimulation.TICK_SECONDS));
        var progress = breaking.progress().orElseThrow();
        assertEquals(position, progress.block());
        assertEquals(59 / 60d, progress.fraction(), 1e-12);
        assertEquals(BlockId.STONE, world.getBlock(position).orElseThrow());
        assertTrue(breaking.tick(Mode.SURVIVAL, hit, true, false, FixedSimulation.TICK_SECONDS));
        assertTrue(breaking.progress().isEmpty());
        assertEquals(BlockId.STONE, world.getBlock(position).orElseThrow());
        assertEquals(59 / 60d, progress.fraction(), 1e-12);
    }

    @Test
    void releaseMissAndNewPressEachDiscardPartialProgress() {
        var hit = hit(position, BlockId.STONE);
        for (int reset = 0; reset < 3; reset++) {
            breaking.tick(Mode.SURVIVAL, hit, true, true, .75);
            if (reset == 0) assertFalse(breaking.tick(Mode.SURVIVAL, hit, false, false, .25));
            else if (reset == 1) assertFalse(breaking.tick(Mode.SURVIVAL, Optional.empty(), true, false, .25));
            else assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, true, 0));
            assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, false, .25));
            assertEquals(.25, breaking.progress().orElseThrow().fraction(), 1e-12);
        }
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, false, true, 2));
    }

    @Test
    void changingPositionBlockTypeOrChunkIdentityRestartsMining() {
        var first = hit(position, BlockId.STONE);
        breaking.tick(Mode.SURVIVAL, first, true, true, .75);
        var other = hit(new BlockPos(4, 1, 1), BlockId.STONE);
        assertFalse(breaking.tick(Mode.SURVIVAL, other, true, false, .25));
        assertEquals(.25, breaking.progress().orElseThrow().fraction(), 1e-12);
        var dirt = hit(other.orElseThrow().block(), BlockId.DIRT);
        assertFalse(breaking.tick(Mode.SURVIVAL, dirt, true, false, .25));
        assertEquals(.25, breaking.progress().orElseThrow().fraction(), 1e-12);
        world.removeChunk(new ChunkPos(0, 0, 0));
        var reloaded = hit(other.orElseThrow().block(), BlockId.DIRT);
        assertFalse(breaking.tick(Mode.SURVIVAL, reloaded, true, false, .25));
        assertEquals(.25, breaking.progress().orElseThrow().fraction(), 1e-12);
    }

    @Test
    void editsToOtherBlocksDoNotResetProgressButReplacingTheTargetDoes() {
        var hit = hit(position, BlockId.STONE);
        breaking.tick(Mode.SURVIVAL, hit, true, true, .5);
        BlockPos neighbor = new BlockPos(4, 1, 1);
        hit(neighbor, BlockId.DIRT);
        breaking.blockChanged(neighbor);
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, false, .25));
        assertEquals(.75, breaking.progress().orElseThrow().fraction(), 1e-12);
        breaking.blockChanged(position);
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, false, .25));
        assertEquals(.25, breaking.progress().orElseThrow().fraction(), 1e-12);
    }

    @Test
    void staleAndUnavailableHitsCannotFinishMining() {
        var hit = hit(position, BlockId.STONE);
        breaking.tick(Mode.SURVIVAL, hit, true, true, .75);
        hit(position, BlockId.DIRT);
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, false, .25));
        assertTrue(breaking.progress().isEmpty());
        world.removeChunk(new ChunkPos(0, 0, 0));
        assertFalse(breaking.tick(Mode.CREATIVE, hit, true, true, 1));
    }

    @Test
    void creativeClicksAreImmediateEvenIfReleasedBeforeTheSimulationTick() {
        var hit = hit(position, BlockId.STONE);
        assertTrue(breaking.tick(Mode.CREATIVE, hit, false, true, 0));
        assertTrue(breaking.progress().isEmpty());
        assertTrue(breaking.tick(Mode.CREATIVE, hit, true, true, 0));
        assertFalse(breaking.tick(Mode.CREATIVE, hit, true, false, 0));
    }

    @Test
    void heldCreativeMiningRepeatsAfterTwelveTicksInsteadOfDeletingEveryTick() {
        var hit = hit(position, BlockId.STONE);
        assertTrue(breaking.tick(Mode.CREATIVE, hit, true, true, FixedSimulation.TICK_SECONDS));
        for (int tick = 1; tick < 12; tick++)
            assertFalse(breaking.tick(Mode.CREATIVE, hit, true, false, FixedSimulation.TICK_SECONDS));
        assertTrue(breaking.tick(Mode.CREATIVE, hit, true, false, FixedSimulation.TICK_SECONDS));
    }

    @Test
    void changingModeAndExplicitCancellationNeverCarrySurvivalProgress() {
        var hit = hit(position, BlockId.STONE);
        breaking.tick(Mode.SURVIVAL, hit, true, true, .75);
        assertFalse(breaking.tick(Mode.SPECTATOR, hit, true, true, 1));
        assertTrue(breaking.progress().isEmpty());
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, false, .25));
        breaking.cancel();
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, false, .25));
        assertEquals(.25, breaking.progress().orElseThrow().fraction(), 1e-12);
        assertTrue(breaking.tick(Mode.CREATIVE, hit, true, false, 0));
        assertFalse(breaking.tick(Mode.SURVIVAL, hit, true, false, .25));
    }

    @Test
    void plantsAreInstantWhileUnbreakableBlocksCannotBeMinedInSurvival() {
        assertTrue(breaking.tick(Mode.SURVIVAL, hit(position, BlockId.POPPY), true, true, 0));
        var water = hit(position, BlockId.WATER);
        assertFalse(breaking.tick(Mode.SURVIVAL, water, true, true, 10));
        assertTrue(breaking.progress().isEmpty());
        assertTrue(breaking.tick(Mode.CREATIVE, water, true, true, 0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidSimulationTime(double seconds) {
        var hit = hit(position, BlockId.STONE);
        assertThrows(IllegalArgumentException.class, () -> breaking.tick(Mode.SURVIVAL, hit, true, true, seconds));
        assertTrue(breaking.progress().isEmpty());
    }

    private Optional<VoxelRaycaster.Hit> hit(BlockPos position, byte block) {
        ChunkPos chunk = Coordinates.worldToChunk(position);
        if (!world.hasChunk(chunk)) world.putChunk(chunk, new Chunk());
        BlockPos local = Coordinates.worldToLocal(position);
        world.getChunk(chunk).set(local.x(), local.y(), local.z(), block);
        return Optional.of(new VoxelRaycaster.Hit(position, new VoxelRaycaster.Normal(-1, 0, 0), 1, block));
    }
}
