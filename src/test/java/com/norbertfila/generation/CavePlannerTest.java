package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CavePlannerTest {
    @Test
    void regionalPlansAreDeterministicAndIndependentOfPlanningOrder() {
        var first = new CavePlanner(42, -32, (x, z) -> 64);
        var expected = first.plan(-1, 2);
        first.plan(50, -50);
        var actual = first.plan(-1, 2);
        var recreated = new CavePlanner(42, -32, (x, z) -> 64).plan(-1, 2);
        assertFalse(all(expected, -1, 2).isEmpty());
        assertEquals(all(expected, -1, 2), all(actual, -1, 2));
        assertEquals(all(expected, -1, 2), all(recreated, -1, 2));
        assertNotEquals(all(expected, -1, 2), all(new CavePlanner(43, -32, (x, z) -> 64).plan(-1, 2), -1, 2));
        for (int z = 10; z < 30; z++)
            for (int x = -20; x < 10; x++) assertEquals(expected.segments(x, z), actual.segments(x, z));
    }

    @Test
    void allBranchesAndChambersStayInsideTheRegionalEnvelopeAndBudget() {
        boolean chamber = false;
        for (int seed = 0; seed < 24; seed++) {
            var planner = new CavePlanner(seed, -32, (x, z) -> 64);
            for (int region : new int[] {-1, 0, 1}) {
                var segments = all(planner.plan(region, region), region, region);
                assertFalse(segments.isEmpty());
                assertTrue(segments.size() <= CavePlanner.MAX_SEGMENTS);
                for (var segment : segments) {
                    double min = region * CavePlanner.REGION_SIZE - CavePlanner.MAX_REACH;
                    double max = (region + 1) * CavePlanner.REGION_SIZE + CavePlanner.MAX_REACH;
                    assertTrue(segment.x() - segment.radiusXZ() >= min);
                    assertTrue(segment.x() + segment.radiusXZ() <= max);
                    assertTrue(segment.z() - segment.radiusXZ() >= min);
                    assertTrue(segment.z() + segment.radiusXZ() <= max);
                    chamber |= segment.radiusXZ() > 4.2;
                }
            }
        }
        assertTrue(chamber);
    }

    @Test
    void shallowTerrainAndRegionsOutsideTheWorldProduceNoCaves() {
        var planner = new CavePlanner(0, 31, (x, z) -> 32);
        assertTrue(all(planner.plan(0, 0), 0, 0).isEmpty());
        assertTrue(planner.plan(Integer.MAX_VALUE, 0).segments(0, 0).isEmpty());
    }

    @Test
    void classicPathsKeepTwoAirBlocksAtTheirVoxelizedCenterInsteadOfPinchingAtTheEnds() {
        for (long seed = 0; seed < 8; seed++) {
            var planner = new CavePlanner(seed, -64, (x, z) -> 128);
            for (var segment : all(planner.plan(0, 0), 0, 0)) {
                if (segment.radiusXZ() > 2.5) continue;
                double x = Math.floor(segment.x()) + .5;
                double y = Math.floor(segment.y()) + .5;
                double z = Math.floor(segment.z()) + .5;
                assertTrue(segment.contains(x, y, z));
                assertTrue(segment.contains(x, y + 1, z));
                assertTrue(segment.radiusXZ() >= 1.3);
            }
        }
    }

    private static Set<CavePlan.Segment> all(CavePlan plan, int regionX, int regionZ) {
        Set<CavePlan.Segment> result = new HashSet<>();
        for (int z = regionZ * 8 - 6; z <= regionZ * 8 + 13; z++)
            for (int x = regionX * 8 - 6; x <= regionX * 8 + 13; x++) result.addAll(plan.segments(x, z));
        return result;
    }
}
