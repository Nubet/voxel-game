package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BadlandsSurface;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.world.BlockId;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BadlandsSurfaceTest {
    private static final Set<Byte> PALETTE = Set.of(
            BlockId.TERRACOTTA,
            BlockId.TERRACOTTA_ORANGE,
            BlockId.TERRACOTTA_RED,
            BlockId.TERRACOTTA_YELLOW,
            BlockId.TERRACOTTA_BROWN,
            BlockId.TERRACOTTA_WHITE,
            BlockId.TERRACOTTA_LIGHT_GRAY);

    @Test
    void bandsUseOnlyTheSevenNaturalTerracottas() {
        for (long seed : new long[] {0, 42, -123456789}) {
            var surface = new BadlandsSurface(seed);
            var found = new HashSet<Byte>();
            int base = 0, pale = 0;
            for (int y = 0; y < 192; y++) {
                byte material = surface.bandAt(y);
                found.add(material);
                if (material == BlockId.TERRACOTTA || material == BlockId.TERRACOTTA_ORANGE) base++;
                if (material == BlockId.TERRACOTTA_WHITE || material == BlockId.TERRACOTTA_LIGHT_GRAY) pale++;
            }
            assertEquals(PALETTE, found);
            assertTrue(base > 96);
            assertTrue(pale < 24);
        }
    }

    @Test
    void seedDeterminesOneRepeatableBandSequenceIncludingNegativeHeights() {
        var first = new BadlandsSurface(42);
        var same = new BadlandsSurface(42);
        var different = new BadlandsSurface(43);
        assertEquals(first.column(-17, 16), same.column(-17, 16));
        assertEquals(first.column(-17, 16).hashCode(), same.column(-17, 16).hashCode());
        boolean differs = false;
        for (int y = -384; y <= 384; y++) {
            assertEquals(first.bandAt(y), same.bandAt(y));
            assertEquals(first.bandAt(y), first.bandAt(y + 192));
            differs |= first.bandAt(y) != different.bandAt(y);
        }
        assertTrue(differs);
    }

    @Test
    void offsetsVarySmoothlyAcrossWorldAndChunkBoundaries() {
        var surface = new BadlandsSurface(42);
        var same = new BadlandsSurface(42);
        var offsets = new HashSet<Integer>();
        for (int z = -1024; z <= 1024; z += 31)
            for (int x = -1024; x <= 1024; x++) {
                var column = surface.column(x, z);
                offsets.add(column.offset());
                assertTrue(Math.abs(column.offset()) <= 7);
                assertEquals(column.offset(), same.column(x, z).offset());
                assertTrue(Math.abs(column.offset() - surface.column(x + 1, z).offset()) <= 1);
                assertTrue(Math.abs(column.offset() - surface.column(x, z + 1).offset()) <= 1);
                assertEquals(surface.bandAt(80L + column.offset()), column.bandAt(80));
            }
        assertTrue(offsets.size() > 3);
    }

    @Test
    void offsetsCannotOverflowAtExtremeHeights() {
        var surface = new BadlandsSurface(42);
        for (int offset : new int[] {-7, 7}) {
            var column = new BadlandsSurface.Column(surface, offset);
            for (int y : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE})
                assertEquals(surface.bandAt((long) y + offset), column.bandAt(y));
        }
        assertThrows(IllegalArgumentException.class, () -> new BadlandsSurface.Column(surface, 8));
        assertThrows(IllegalArgumentException.class, () -> new BadlandsSurface.Column(surface, -8));
    }

    @Test
    void lowerDrySurfacesHaveRedSandOverOrangeTerracotta() {
        var column = new BadlandsSurface(42).column(-17, 16);
        for (int seaLevel : new int[] {-20, 28, 63}) {
            for (int height : new int[] {seaLevel, seaLevel + 16, seaLevel + 32}) {
                byte top = column.surfaceAt(height, seaLevel);
                assertEquals(BlockId.RED_SAND, top);
                assertEquals(top, column.materialAt(height, height, top));
                for (int depth = 1; depth <= 3; depth++)
                    assertEquals(BlockId.TERRACOTTA_ORANGE, column.materialAt(height - depth, height, top));
                assertEquals(column.bandAt(height - 4), column.materialAt(height - 4, height, top));
            }
            for (int height : new int[] {seaLevel - 1, seaLevel + 33})
                assertEquals(column.bandAt(height), column.surfaceAt(height, seaLevel));
        }
    }

    @Test
    void highSurfacesExposeBandsAndReturnToStoneAtDepth() {
        var column = new BadlandsSurface(42).column(513, -257);
        byte top = column.surfaceAt(120, 28);
        assertEquals(column.bandAt(120), top);
        for (int y = 24; y <= 120; y++) assertEquals(column.bandAt(y), column.materialAt(y, 120, top));
        assertEquals(BlockId.STONE, column.materialAt(23, 120, top));
        assertThrows(IllegalArgumentException.class, () -> column.materialAt(121, 120, top));
    }

    @Test
    void summariesAndCachedColumnsShareLayersWithoutFlatteningSections() {
        var surface = new BadlandsSurface(42);
        for (int height : new int[] {48, 100}) {
            var summaries = new TerrainSummary[TerrainColumn.AREA];
            for (int index = 0; index < summaries.length; index++) {
                var layers = surface.column(index % 16 - 16, index / 16);
                byte top = layers.surfaceAt(height, 28);
                summaries[index] = new TerrainSummary(
                        height,
                        top,
                        layers.materialAt(height - 1, height, top),
                        BlockId.STONE,
                        -128,
                        28,
                        BiomeType.BADLANDS,
                        new ClimateSample(.5f, 0),
                        WaterKind.OCEAN,
                        false,
                        false,
                        layers);
            }
            var column = TerrainColumn.of(summaries, java.util.List.of());
            for (int index = 0; index < summaries.length; index++)
                for (int y = -129; y <= height + 1; y++)
                    assertEquals(summaries[index].materialAt(y), column.materialAt(index, y));
            assertEquals(-1, column.uniformBlock(height - 32));
            assertEquals(BlockId.STONE, column.uniformBlock(height - 128));
            assertEquals(BlockId.BEDROCK, column.materialAt(0, -128));
            assertEquals(BlockId.AIR, column.materialAt(0, -129));
        }
    }

    @Test
    void redTerracottaAloneDoesNotEnableBadlandsGeneration() {
        var summary = new TerrainSummary(80, BlockId.TERRACOTTA_RED, BlockId.TERRACOTTA_RED, BlockId.TERRACOTTA_RED);
        assertEquals(BlockId.TERRACOTTA_RED, summary.materialAt(31));
    }
}
