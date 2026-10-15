package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GpuArenaTest {
    @Test
    void alignsAllocationsAndMergesFreedBlocks() {
        GpuArena arena = new GpuArena(256, 16);
        GpuArena.Allocation first = arena.allocate(24);
        GpuArena.Allocation second = arena.allocate(32);

        assertEquals(0, first.offset());
        assertEquals(32, second.offset());
        arena.free(first);
        arena.free(second);

        assertEquals(256, arena.freeBytes());
        assertEquals(256, arena.largestFreeBlockBytes());
        assertEquals(0, arena.fragmentationBytes());
    }

    @Test
    void rejectsAllocationWhenOnlyFragmentedSpaceRemains() {
        GpuArena arena = new GpuArena(128, 16);
        GpuArena.Allocation first = arena.allocate(32);
        GpuArena.Allocation second = arena.allocate(32);
        GpuArena.Allocation third = arena.allocate(32);
        GpuArena.Allocation fourth = arena.allocate(32);
        arena.free(first);
        arena.free(third);

        assertThrows(IllegalStateException.class, () -> arena.allocate(48));
        arena.free(second);
        arena.free(fourth);
    }
}
