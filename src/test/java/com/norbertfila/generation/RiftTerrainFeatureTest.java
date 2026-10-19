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

    @Test
    void ledgesExposeStoneWithoutReplacingTheUncutGrass() {
        assertEquals(
                RiftTerrainFeature.carve(STRAIGHT, 0, -3, GROUND).surfaceHeight(),
                RiftTerrainFeature.carve(STRAIGHT, 0, -4, GROUND).surfaceHeight());
        assertEquals(
                BlockId.STONE, RiftTerrainFeature.carve(STRAIGHT, 0, -3, GROUND).surfaceMaterial());
        assertSame(GROUND, RiftTerrainFeature.carve(STRAIGHT, 0, 5, GROUND));
    }

    @Test
    void rejectsTheWholeRiftForSingleBlockWaterOrSteepTerrainOnItsRoute() {
        var plan = new RiftPlanner(42).plan(-99, 0);
        for (int x = -100; x < 100 && plan.isEmpty(); x++) plan = new RiftPlanner(42).plan(x, 0);
        var p = plan.orElseThrow();
        var node = p.nodes().get(3);
        int cx = (int) node.x(), cz = (int) node.z();
        var dry = new RiftTerrainFeature(42, (x, z) -> GROUND);
        assertEquals(p, dry.planAt(cx, cz));
        var water = new TerrainSummary(60, BlockId.GRAVEL, BlockId.STONE, BlockId.STONE, -64, 62);
        var wet = new RiftTerrainFeature(42, (x, z) -> x == cx && z == cz ? water : GROUND);
        assertNull(wet.planAt(cx, cz));
        int[] bank = null;
        search:
        for (int z = p.minZ() - 6; z <= p.maxZ() + 6; z++)
            for (int x = p.minX() - 6; x <= p.maxX() + 6; x++)
                if (p.section(x + .5, z + .5) == null && p.section(x + .5, z + .5, 6) != null) {
                    bank = new int[] {x, z};
                    break search;
                }
        assertNotNull(bank);
        int bankX = bank[0], bankZ = bank[1];
        var wetBank = new RiftTerrainFeature(42, (x, z) -> x == bankX && z == bankZ ? water : GROUND);
        assertNull(wetBank.planAt(cx, cz));
        var cliff = new RiftTerrainFeature(
                42,
                (x, z) -> x == cx && z == cz
                        ? new TerrainSummary(100, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, -64)
                        : GROUND);
        assertNull(cliff.planAt(cx, cz));
    }
}
