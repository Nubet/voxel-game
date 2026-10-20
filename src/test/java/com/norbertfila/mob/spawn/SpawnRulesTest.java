package com.norbertfila.mob.spawn;

import static com.norbertfila.mob.spawn.SpawnTestWorld.CAVE_FLOOR;
import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.*;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpawnRulesTest {
    private static final int DAY = 6000, NIGHT = 18000;
    private static final List<PlayerPosition> PLAYER = List.of(new PlayerPosition(8, 0, 8));
    private static final SpawnEntry ZOMBIE = SpawnEntry.everywhere(MobType.ZOMBIE, 1, 1, 1);
    private final SpawnTestWorld test = new SpawnTestWorld(true);
    private final MobPopulation population = new MobPopulation();

    @Test
    void surfaceNeedsNightAndCavesSpawnAnyTime() {
        assertTrue(allows(NIGHT, 40, 0, 8));
        assertFalse(allows(DAY, 40, 0, 8));
        assertTrue(allows(DAY, 40, CAVE_FLOOR, 8));
        assertTrue(allows(NIGHT, 40, CAVE_FLOOR, 8));
        assertEquals(SpawnZone.SURFACE, attempt(NIGHT, 40, 0, 8).zone().orElseThrow());
        assertEquals(SpawnZone.CAVE, attempt(NIGHT, 40, CAVE_FLOOR, 8).zone().orElseThrow());
    }

    @Test
    void distanceToPlayersIsMeasuredInThreeDimensions() {
        assertFalse(allows(NIGHT, 28, 0, 8));
        assertTrue(allows(NIGHT, 33, 0, 8));
        assertFalse(allows(DAY, 20, CAVE_FLOOR, 8));
        assertTrue(allows(DAY, 25, CAVE_FLOOR, 8));
        assertFalse(
                SpawnRules.HOSTILE.allows(new SpawnAttempt(context(NIGHT, List.of()), ZOMBIE, new BlockPos(40, 0, 8))));
        assertFalse(SpawnRules.HOSTILE.allows(new SpawnAttempt(
                context(NIGHT, List.of(new PlayerPosition(-80, 0, 8))), ZOMBIE, new BlockPos(70, 0, 8))));
    }

    @Test
    void torchLightKeepsCavesClear() {
        test.world.setBlock(new BlockPos(46, CAVE_FLOOR, 8), BlockId.TORCH, 64);
        assertFalse(allows(DAY, 40, CAVE_FLOOR, 8));
        assertTrue(allows(DAY, 70, CAVE_FLOOR, 8));
    }

    @Test
    void groundSpaceFluidsAndOtherMobsBlockSpawning() {
        assertFalse(allows(NIGHT, 40, 1, 8));
        test.world.setBlock(new BlockPos(40, 0, 8), BlockId.WATER, 64);
        assertFalse(allows(NIGHT, 40, 0, 8));
        test.world.setBlock(new BlockPos(40, 1, 12), BlockId.STONE, 64);
        assertFalse(allows(NIGHT, 40, 0, 12));
        test.world.setBlock(new BlockPos(40, -1, 16), BlockId.BEDROCK, 64);
        assertFalse(allows(NIGHT, 40, 0, 16));
        test.world.setBlock(new BlockPos(40, -1, 20), BlockId.GLASS, 64);
        assertFalse(allows(NIGHT, 40, 0, 20));
        population.spawn(MobType.CREEPER, SpawnZone.SURFACE, 40.7, 0, 24.5, 0);
        assertFalse(allows(NIGHT, 40, 0, 24));
        assertTrue(allows(NIGHT, 40, 0, 28));
    }

    @Test
    void fullZoneCapsRejectOnlyTheirOwnZone() {
        for (int i = 0; i < 40; i++) population.spawn(MobType.ZOMBIE, SpawnZone.SURFACE, -60, 0, -60 - i, 0);
        assertFalse(allows(NIGHT, 40, 0, 8));
        assertTrue(allows(NIGHT, 40, CAVE_FLOOR, 8));
    }

    @Test
    void zonesListedOnTheEntryAreRespected() {
        var surfaceOnly = new SpawnEntry(MobType.SKELETON, 1, 1, 1, java.util.Set.of(SpawnZone.SURFACE));
        assertFalse(SpawnRules.HOSTILE.allows(
                new SpawnAttempt(context(DAY, PLAYER), surfaceOnly, new BlockPos(40, CAVE_FLOOR, 8))));
        assertTrue(SpawnRules.HOSTILE.allows(
                new SpawnAttempt(context(NIGHT, PLAYER), surfaceOnly, new BlockPos(40, 0, 8))));
    }

    private boolean allows(int time, int x, int y, int z) {
        return SpawnRules.HOSTILE.allows(attempt(time, x, y, z));
    }

    private SpawnAttempt attempt(int time, int x, int y, int z) {
        return new SpawnAttempt(context(time, PLAYER), ZOMBIE, new BlockPos(x, y, z));
    }

    private SpawnContext context(int time, List<PlayerPosition> players) {
        return new SpawnContext(
                test.environment(time),
                population,
                players,
                SpawnTestWorld.settings(40, 30),
                SpawnTestWorld.highestRoll(),
                81);
    }
}
