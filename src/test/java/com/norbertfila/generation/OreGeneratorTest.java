package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OreGeneratorTest {
    @Test
    void deterministicAcrossSeedsOrderAndWorkersWithEveryOreInItsHeightRange() {
        var generator = new OreGenerator(42);
        var positions = new java.util.ArrayList<ChunkPos>();
        for (int cy = -5; cy <= 4; cy++)
            for (int cz = -2; cz <= 1; cz++) for (int cx = -2; cx <= 1; cx++) positions.add(new ChunkPos(cx, cy, cz));
        var expected = positions.stream().map(p -> generate(generator, p)).toList();
        var parallel =
                positions.parallelStream().map(p -> generate(generator, p)).toList();
        var seen = new java.util.HashSet<Byte>();
        boolean differentSeed = false;
        for (int i = positions.size() - 1; i >= 0; i--) {
            var position = positions.get(i);
            byte[] blocks = expected.get(i);
            assertArrayEquals(blocks, parallel.get(i));
            assertArrayEquals(blocks, generate(new OreGenerator(42), position));
            differentSeed |= !Arrays.equals(blocks, generate(new OreGenerator(43), position));
            for (int index = 0; index < blocks.length; index++) {
                if (blocks[index] == BlockId.STONE) continue;
                byte block = blocks[index];
                seen.add(block);
                var ore = OreGenerator.DEFAULT_ORES.stream()
                        .filter(o -> o.block() == block)
                        .findFirst()
                        .orElseThrow();
                int y = position.y() * 16 + index / 256;
                assertTrue(y >= ore.minY() && y <= ore.maxY());
            }
        }
        assertEquals(5, seen.size());
        assertTrue(differentSeed);
    }

    @Test
    void onlyStoneIsReplacedAndEmptySectionsStayUnchanged() {
        var generator = new OreGenerator(42);
        var position = new ChunkPos(-1, -4, 0);
        byte[] mixed = generate(generator, position);
        int target = 0;
        while (mixed[target] == BlockId.STONE) target++;
        for (byte protectedBlock :
                new byte[] {BlockId.AIR, BlockId.WATER, BlockId.BEDROCK, BlockId.DIRT, BlockId.COAL_ORE}) {
            byte[] blocks = stone();
            blocks[target] = protectedBlock;
            generator.generate(position, blocks);
            assertEquals(protectedBlock, blocks[target]);
        }
        byte[] empty = new byte[ChunkData.BLOCK_COUNT];
        assertFalse(generator.generate(position, empty));
        assertArrayEquals(new byte[ChunkData.BLOCK_COUNT], empty);
        assertThrows(IllegalArgumentException.class, () -> generator.generate(position, new byte[1]));
    }

    @Test
    void veinsCrossSignedHorizontalAndVerticalSectionBoundaries() {
        var ore = new OreDefinition(BlockId.DIAMOND_ORE, -17, -16, 8, 32, OreDefinition.HeightDistribution.UNIFORM, 7);
        var generator = new OreGenerator(5, List.of(ore));
        byte[] lowerLeft = generate(generator, new ChunkPos(-1, -2, -1));
        byte[] upperLeft = generate(generator, new ChunkPos(-1, -1, -1));
        byte[] lowerRight = generate(generator, new ChunkPos(0, -2, -1));
        byte[] lowerFront = generate(generator, new ChunkPos(-1, -2, 0));
        boolean vertical = false, horizontalX = false, horizontalZ = false;
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                vertical |= lowerLeft[(15 * 16 + z) * 16 + x] == BlockId.DIAMOND_ORE
                        && upperLeft[z * 16 + x] == BlockId.DIAMOND_ORE;
                horizontalX |= lowerLeft[(15 * 16 + z) * 16 + 15] == BlockId.DIAMOND_ORE
                        && lowerRight[(15 * 16 + z) * 16] == BlockId.DIAMOND_ORE;
                horizontalZ |= lowerLeft[(15 * 16 + 15) * 16 + x] == BlockId.DIAMOND_ORE
                        && lowerFront[15 * 256 + x] == BlockId.DIAMOND_ORE;
            }
        assertTrue(vertical);
        assertTrue(horizontalX);
        assertTrue(horizontalZ);
    }

    @Test
    void validatesDefinitionsAndConcentratesLapisNearTheMiddle() {
        var lapis = OreGenerator.DEFAULT_ORES.get(4);
        var random = new java.util.Random(7);
        int center = 0, edge = 0;
        for (int i = 0; i < 10_000; i++) {
            int y = lapis.height(random);
            assertTrue(y >= -64 && y <= -34);
            if (Math.abs(y + 49) <= 3) center++;
            if (y <= -61 || y >= -37) edge++;
        }
        assertTrue(center > edge * 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreDefinition(BlockId.COAL_ORE, -65, 0, 1, 8, OreDefinition.HeightDistribution.UNIFORM, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreDefinition(BlockId.COAL_ORE, 0, 1, 1, 33, OreDefinition.HeightDistribution.UNIFORM, 0));
    }

    private static byte[] generate(OreGenerator generator, ChunkPos position) {
        byte[] blocks = stone();
        generator.generate(position, blocks);
        return blocks;
    }

    private static byte[] stone() {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        Arrays.fill(blocks, BlockId.STONE);
        return blocks;
    }
}
