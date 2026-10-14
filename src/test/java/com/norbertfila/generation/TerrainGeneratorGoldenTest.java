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
import org.junit.jupiter.params.provider.CsvSource;

/** Pins generator output to checksums recorded from the uncached generator, so caching cannot change the world. */
class TerrainGeneratorGoldenTest {
    @ParameterizedTest
    @CsvSource({"0, cf589f8f", "7340215, 43585451", "-123456789, da365b25"})
    void matchesRecordedWorldInSequentialOrder(long seed, String checksum) {
        var generator = new TerrainGenerator(seed, TerrainConfig.defaults());
        CRC32 crc = new CRC32();
        for (ChunkPos position : positions())
            crc.update(generator.generate(position).blocks());
        assertEquals(checksum, Long.toHexString(crc.getValue()));
    }

    @ParameterizedTest
    @CsvSource({"0, cf589f8f", "-123456789, da365b25"})
    void matchesRecordedWorldWhenSectionsArriveShuffledAcrossWorkers(long seed, String checksum) throws Exception {
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
        CRC32 crc = new CRC32();
        for (byte[] section : blocks) crc.update(section);
        assertEquals(checksum, Long.toHexString(crc.getValue()));
    }

    private static List<ChunkPos> positions() {
        List<ChunkPos> positions = new ArrayList<>();
        for (int cx = -3; cx <= 2; cx++)
            for (int cz = -2; cz <= 3; cz++)
                for (int cy = -3; cy <= 8; cy++) positions.add(new ChunkPos(cx * 7 - 40, cy, cz * 5 + 11));
        return positions;
    }
}
