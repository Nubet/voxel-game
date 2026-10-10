package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class LodTransitionTest {
    @Test
    void hysteresisPreventsBoundaryFlapping() {
        LodLevel near = new LodLevel(0, 8, 4);
        LodLevel far = new LodLevel(9, 16, 8);

        assertEquals(near, LodTransition.selectLevel(java.util.List.of(near, far), near, 81, 1));
        assertEquals(far, LodTransition.selectLevel(java.util.List.of(near, far), near, 100, 1));
        assertEquals(far, LodTransition.selectLevel(java.util.List.of(near, far), far, 64, 1));
        assertEquals(near, LodTransition.selectLevel(java.util.List.of(near, far), far, 49, 1));
    }

    @Test
    void seamConnectsDifferentSampleDensitiesAtSharedEdge() {
        int[] fineHeights = new int[17 * 17];
        int[] coarseHeights = new int[9 * 9];
        java.util.Arrays.fill(fineHeights, 10);
        java.util.Arrays.fill(coarseHeights, 12);
        FarTerrainTile fine = new FarTerrainTile(0, 0, 64, 4, fineHeights, materials(fineHeights.length));
        FarTerrainTile coarse = new FarTerrainTile(64, 0, 64, 8, coarseHeights, materials(coarseHeights.length));

        var seam = LodTransition.buildSeam(fine, coarse, LodTransition.Edge.POSITIVE_X);

        assertEquals(16 * 6, seam.vertexCount());
        assertTrue(seam.format().bounds().minX() == 64);
        assertEquals(BlockId.GRASS, fine.materialAt(16, 0));
    }

    private static byte[] materials(int count) {
        byte[] materials = new byte[count];
        java.util.Arrays.fill(materials, BlockId.GRASS);
        return materials;
    }
}
