package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.meshing.QuadRanges;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GpuMeshTest {
    @Test
    void keepsItsPlaceInTheSharedBuffer() {
        GpuMesh mesh = new GpuMesh(7, 120, QuadMesh.Format.SOLID, new QuadRanges(3, 2, 1), 48, () -> {});
        assertEquals(7, mesh.buffer());
        assertEquals(120, mesh.firstRecord());
        assertEquals(new QuadRanges(3, 2, 1), mesh.ranges());
        assertEquals(48, mesh.bytes());
    }

    @Test
    void closeReleasesTheRangeOnlyOnce() {
        AtomicInteger releases = new AtomicInteger();
        GpuMesh mesh = new GpuMesh(7, 0, QuadMesh.Format.WATER, QuadRanges.EMPTY, 0, releases::incrementAndGet);

        mesh.close();
        mesh.close();

        assertTrue(mesh.closed());
        assertEquals(1, releases.get());
    }
}
