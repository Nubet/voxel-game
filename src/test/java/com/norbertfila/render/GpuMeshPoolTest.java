package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.meshing.QuadRanges;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpuMeshPoolTest {
    @Test
    void replacementIsDeletedThroughThePool() {
        List<String> deleted = new ArrayList<>();
        GpuMeshPool pool = new GpuMeshPool(new GpuMemoryBudget(300));
        ChunkPos position = new ChunkPos(2, 3, 4);
        pool.put(position, mesh(7, 11, deleted));
        pool.put(position, mesh(13, 17, deleted));

        assertEquals(1, pool.size());
        assertEquals(1, pool.pendingDeletionCount());
        assertEquals(1, pool.drainDeletions(1).count());
        assertEquals(List.of("7:11"), deleted);
        pool.close();
        assertEquals(List.of("7:11", "13:17"), deleted);
    }

    @Test
    void budgetFailureDoesNotLeakTheRejectedMesh() {
        List<String> deleted = new ArrayList<>();
        GpuMeshPool pool = new GpuMeshPool(new GpuMemoryBudget(200));
        pool.put(new ChunkPos(0, 0, 0), mesh(1, 2, deleted));

        assertThrows(IllegalStateException.class, () -> pool.put(new ChunkPos(1, 0, 0), mesh(3, 4, deleted)));
        assertEquals(1, pool.size());
        pool.close();
        assertEquals(List.of("1:2"), deleted);
    }

    @Test
    void repeatedLoadAndEvictReturnsToAStableBudget() {
        GpuMemoryBudget budget = new GpuMemoryBudget(200);
        GpuMeshPool pool = new GpuMeshPool(budget);
        for (int index = 0; index < 1000; index++) {
            ChunkPos position = new ChunkPos(index, 0, 0);
            pool.put(position, mesh(index, index, new ArrayList<>()));
            pool.remove(position);
            pool.drainDeletions(1);
        }

        assertEquals(0, pool.size());
        assertEquals(0, pool.pendingDeletionCount());
        assertEquals(0, budget.allocatedBytes());
        assertEquals(144_000, budget.uploadedBytes());
        assertEquals(144_000, budget.deletedBytes());
    }

    /** A 144-byte mesh whose deletion records both ids, so tests can tell instances apart. */
    private static GpuMesh mesh(int first, int second, List<String> deleted) {
        return new GpuMesh(
                first,
                QuadMesh.Format.SOLID,
                new QuadRanges(18, 0, 0),
                144,
                buffer -> deleted.add(first + ":" + second));
    }
}
