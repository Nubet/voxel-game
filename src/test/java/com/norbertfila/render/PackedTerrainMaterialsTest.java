package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.meshing.PackedVertex;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class PackedTerrainMaterialsTest {
    @Test
    void acceptsOpaqueAndCutoutMaterials() {
        assertDoesNotThrow(() -> PackedTerrainRenderer.requireSupportedMaterials(
                new int[] {vertex(BlockId.STONE), vertex(BlockId.LEAVES), vertex(BlockId.GLASS)}));
        assertDoesNotThrow(() -> PackedTerrainRenderer.requireSupportedMaterials(new int[0]));
    }

    @Test
    void rejectsTranslucencyAnywhereInTheBuffer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PackedTerrainRenderer.requireSupportedMaterials(
                        new int[] {vertex(BlockId.STONE), vertex(BlockId.WATER), vertex(BlockId.LEAVES)}));
    }

    private static int vertex(byte block) {
        return PackedVertex.pack(0, 0, 0, 2, block, 0);
    }
}
