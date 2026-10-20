package com.norbertfila.mob.spawn;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobCategory;
import com.norbertfila.mob.MobType;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class SpawnTableTest {
    private static final PopulationKey SURFACE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE);
    private static final PopulationKey CAVE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.CAVE);

    @Test
    void overworldPicksEveryHostileMobRoughlyByWeight() {
        var counts = new EnumMap<MobType, Integer>(MobType.class);
        var random = new SplittableRandom(7);
        for (int i = 0; i < 29_500; i++)
            counts.merge(
                    SpawnTable.OVERWORLD.pick(SURFACE, random).orElseThrow().type(), 1, Integer::sum);
        assertEquals(9500, counts.get(MobType.ZOMBIE), 400);
        assertEquals(10000, counts.get(MobType.SKELETON), 400);
        assertEquals(10000, counts.get(MobType.CREEPER), 400);
    }

    @Test
    void pickIsLimitedToEntriesAllowedInTheZone() {
        var table = new SpawnTable(List.of(
                new SpawnEntry(MobType.ZOMBIE, 1, 1, 1, Set.of(SpawnZone.SURFACE)),
                new SpawnEntry(MobType.CREEPER, 1, 1, 1, Set.of(SpawnZone.CAVE))));
        var random = new SplittableRandom(1);
        for (int i = 0; i < 50; i++) {
            assertEquals(
                    MobType.ZOMBIE, table.pick(SURFACE, random).orElseThrow().type());
            assertEquals(MobType.CREEPER, table.pick(CAVE, random).orElseThrow().type());
        }
        assertTrue(new SpawnTable(List.of()).pick(SURFACE, random).isEmpty());
    }

    @Test
    void entriesRejectInvalidWeightsGroupsAndZones() {
        assertThrows(IllegalArgumentException.class, () -> SpawnEntry.everywhere(MobType.ZOMBIE, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SpawnEntry.everywhere(MobType.ZOMBIE, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> SpawnEntry.everywhere(MobType.ZOMBIE, 1, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> new SpawnEntry(MobType.ZOMBIE, 1, 1, 1, Set.of()));
    }
}
