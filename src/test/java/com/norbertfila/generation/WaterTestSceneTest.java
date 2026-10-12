package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaterTestSceneTest {
    @Test
    void basinCrossesHorizontalAndVerticalChunkBoundariesWithADrySpawnAndViewingFeatures() {
        WaterTestScene scene = new WaterTestScene();
        for (int x : new int[] {-1, 0})
            for (int y : new int[] {15, 16, 17}) assertEquals(BlockId.WATER, block(scene, x, y, -6));
        assertEquals(BlockId.STONE, block(scene, 0, 13, -6));
        assertEquals(BlockId.AIR, block(scene, 0, 18, -6));
        assertEquals(BlockId.GRASS, block(scene, 0, 17, 0));
        assertEquals(BlockId.AIR, block(scene, 0, 18, 0));
        assertEquals(BlockId.STONE, block(scene, 2, 19, -10));
        assertEquals(BlockId.LEAVES, block(scene, -3, 18, -8));
        assertEquals(BlockId.AIR, block(scene, 4, 16, -6));
        assertEquals(17, scene.surfaceHeight(0, 0));
    }

    @Test
    void chunkDataDoesNotDependOnGenerationOrder() {
        WaterTestScene scene = new WaterTestScene();
        List<ChunkPos> positions = List.of(
                new ChunkPos(-1, 0, -1), new ChunkPos(0, 0, -1), new ChunkPos(-1, 1, -1), new ChunkPos(0, 1, -1));
        var expected = positions.stream()
                .map(position -> scene.generate(position).blocks())
                .toList();
        for (int i = positions.size() - 1; i >= 0; i--)
            assertArrayEquals(expected.get(i), scene.generate(positions.get(i)).blocks());
    }

    private static byte block(WaterTestScene scene, int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        BlockPos local = Coordinates.worldToLocal(position);
        return scene.generate(Coordinates.worldToChunk(position)).get(local.x(), local.y(), local.z());
    }
}
