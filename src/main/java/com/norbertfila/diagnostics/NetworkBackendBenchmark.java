package com.norbertfila.diagnostics;

import com.norbertfila.protocol.MessageCodec;
import com.norbertfila.protocol.WorldCommand;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class NetworkBackendBenchmark {
    private static final int MESSAGES = 10_000;

    private NetworkBackendBenchmark() {}

    public static void main(String[] args) throws Exception {
        Map<BlockPos, Byte> blocks = new HashMap<>();
        long[] latencies = new long[MESSAGES];
        long workloadStart = System.nanoTime();
        for (int index = 0; index < MESSAGES; index++) {
            long start = System.nanoTime();
            WorldCommand.BlockEdit edit = new WorldCommand.BlockEdit(
                    new BlockPos(index, 0, 0), index % 2 == 0 ? BlockId.GRASS : BlockId.DIRT);
            WorldCommand.BlockEdit decoded = (WorldCommand.BlockEdit) MessageCodec.decode(MessageCodec.encode(edit));
            blocks.put(decoded.position(), decoded.value());
            latencies[index] = System.nanoTime() - start;
        }
        Arrays.sort(latencies);
        long p99 = latencies[Math.min(MESSAGES - 1, MESSAGES * 99 / 100)];
        long elapsedNanos = System.nanoTime() - workloadStart;
        System.out.print("{\"backend\":\"java\",\"messages\":"
                + MESSAGES
                + ",\"stateBlocks\":"
                + blocks.size()
                + ",\"elapsedNanos\":"
                + elapsedNanos
                + ",\"throughputPerSecond\":"
                + (MESSAGES * 1_000_000_000.0 / elapsedNanos)
                + ",\"p99Nanos\":"
                + p99
                + ",\"ramMeasured\":false,\"gpuMeasured\":false}");
    }
}
