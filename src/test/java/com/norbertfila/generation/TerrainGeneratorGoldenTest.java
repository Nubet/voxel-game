package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TerrainGeneratorGoldenTest {
    @ParameterizedTest
    @ValueSource(longs = {0, 7340215, -123456789})
    void remainsDeterministicInSequentialOrder(long seed) {
        var generator = new TerrainGenerator(seed, TerrainConfig.defaults());
        CRC32 first = new CRC32();
        CRC32 second = new CRC32();
        for (ChunkPos position : positions())
            first.update(generator.generate(position).blocks());
        for (ChunkPos position : positions())
            second.update(generator.generate(position).blocks());
        assertEquals(first.getValue(), second.getValue());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -123456789})
    void remainsDeterministicWhenSectionsArriveShuffledAcrossWorkers(long seed) throws Exception {
        var generator = new TerrainGenerator(seed, TerrainConfig.defaults());
        List<ChunkPos> positions = positions();
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < positions.size(); index++) order.add(index);
        Collections.shuffle(order, new java.util.Random(seed));
        byte[][] blocks = new byte[positions.size()][];
        try (var workers = Executors.newFixedThreadPool(4)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int index : order)
                tasks.add(workers.submit(() ->
                        blocks[index] = generator.generate(positions.get(index)).blocks()));
            for (Future<?> task : tasks) task.get();
        }
        CRC32 shuffled = new CRC32();
        for (byte[] section : blocks) shuffled.update(section);
        CRC32 sequential = new CRC32();
        for (ChunkPos position : positions)
            sequential.update(generator.generate(position).blocks());
        assertEquals(sequential.getValue(), shuffled.getValue());
    }

    private static List<ChunkPos> positions() {
        List<ChunkPos> positions = new ArrayList<>();
        for (int cx = -3; cx <= 2; cx++)
            for (int cz = -2; cz <= 3; cz++)
                for (int cy = -3; cy <= 8; cy++) positions.add(new ChunkPos(cx * 7 - 40, cy, cz * 5 + 11));
        return positions;
    }
}
