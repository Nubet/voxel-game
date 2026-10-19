package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RiftPlannerTest {
    @Test
    void plansAreRareBoundedAndIndependentOfQueryOrder() {
        var planner = new RiftPlanner(42);
        var other = new RiftPlanner(42);
        int count = 0;
        for (int z = -40; z < 40; z++)
            for (int x = -40; x < 40; x++) {
                var plan = planner.plan(x, z);
                assertEquals(plan, other.plan(x, z));
                if (plan.isEmpty()) continue;
                count++;
                var p = plan.get();
                assertTrue(p.minX() >= x * RiftPlanner.REGION_SIZE + 70);
                assertTrue(p.maxX() < (x + 1) * RiftPlanner.REGION_SIZE - 70);
                assertTrue(p.minZ() >= z * RiftPlanner.REGION_SIZE + 70);
                assertTrue(p.maxZ() < (z + 1) * RiftPlanner.REGION_SIZE - 70);
                for (var n : p.nodes()) {
                    assertTrue(n.halfWidth() > 0 && n.halfWidth() <= 7);
                    assertTrue(n.depth() > 0 && n.depth() <= 40);
                    assertEquals(p, planner.at((int) n.x(), (int) n.z()));
                }
            }
        assertTrue(
                count > 1450 && count < 1800, "One candidate per four square regions before terrain checks: " + count);
        IntStream.range(-40, 40).parallel().forEach(x -> assertEquals(other.plan(x, -3), planner.plan(x, -3)));
    }

    @Test
    void endpointsFadeAndCoordinatesDoNotWrap() {
        var planner = new RiftPlanner(42);
        var plan = IntStream.range(-100, 100)
                .mapToObj(x -> planner.plan(x, 0))
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElseThrow();
        var first = plan.nodes().getFirst();
        var last = plan.nodes().getLast();
        assertEquals(0, plan.section(first.x(), first.z()).depth(), 1e-8);
        assertEquals(0, plan.section(last.x(), last.z()).depth(), 1e-8);
        assertNull(plan.section(plan.maxX() + 1, plan.maxZ() + 1));
        assertDoesNotThrow(() -> planner.at(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertTrue(planner.plan(Integer.MAX_VALUE, Integer.MIN_VALUE).isEmpty());
    }
}
