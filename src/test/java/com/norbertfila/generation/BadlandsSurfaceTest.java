package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BadlandsSurface;
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
        boolean differs = false;
        for (int y = -384; y <= 384; y++) {
            assertEquals(first.bandAt(y), same.bandAt(y));
            assertEquals(first.bandAt(y), first.bandAt(y + 192));
            differs |= first.bandAt(y) != different.bandAt(y);
        }
        assertTrue(differs);
    }
}
