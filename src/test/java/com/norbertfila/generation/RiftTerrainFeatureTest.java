package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiftTerrainFeatureTest {
    static final RiftPlanner.Plan STRAIGHT =
            new RiftPlanner.Plan(List.of(new RiftPlanner.Node(-48, .5, 5, 28), new RiftPlanner.Node(48, .5, 5, 28)));
    static final TerrainSummary GROUND = new TerrainSummary(80, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, -64, 62);

    @Test
    void cutsAreNarrowOpenAndAsymmetricWithTaperedEnds() {
        var middle = RiftTerrainFeature.carve(STRAIGHT, 0, 0, GROUND);
        assertEquals(52, middle.surfaceHeight());
        assertEquals(BlockId.STONE, middle.surfaceMaterial());
        assertEquals(BlockId.AIR, middle.materialAt(70));
        assertEquals(BlockId.BEDROCK, middle.materialAt(-64));
        assertSame(GROUND, RiftTerrainFeature.carve(STRAIGHT, 0, 6, GROUND));
        assertTrue(RiftTerrainFeature.carve(STRAIGHT, -47, 0, GROUND).surfaceHeight() > 75);
        assertTrue(RiftTerrainFeature.carve(STRAIGHT, 46, 0, GROUND).surfaceHeight() > 75);
        assertNotEquals(
                RiftTerrainFeature.carve(STRAIGHT, 0, -4, GROUND).surfaceHeight(),
                RiftTerrainFeature.carve(STRAIGHT, 0, 4, GROUND).surfaceHeight());
    }

    @Test
    void floorsStayDryBelowSeaAndAboveBedrock() {
        var shallow = new TerrainSummary(-50, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, -64);
        var result = RiftTerrainFeature.carve(STRAIGHT, 0, 0, shallow);
        assertEquals(-60, result.surfaceHeight());
        assertEquals(BlockId.BEDROCK, result.materialAt(-64));
        var belowSea = RiftTerrainFeature.carve(STRAIGHT, 0, 0, GROUND);
        assertEquals(WaterKind.NONE, belowSea.waterKind());
        for (int y = belowSea.surfaceHeight() + 1; y <= 80; y++) assertEquals(BlockId.AIR, belowSea.materialAt(y));
    }

    @Test
    void geometryIsIndependentOfChunkAndQueryOrder() {
        var cuts = new int[96];
        for (int x = -48; x < 48; x++)
            cuts[x + 48] = RiftTerrainFeature.carve(STRAIGHT, x, 0, GROUND).surfaceHeight();
        for (int x = 47; x >= -48; x--) {
            assertEquals(
                    cuts[x + 48],
                    RiftTerrainFeature.carve(STRAIGHT, x, 0, GROUND).surfaceHeight());
            if (x > -48) assertTrue(Math.abs(cuts[x + 48] - cuts[x + 47]) <= 4);
        }
    }
}
