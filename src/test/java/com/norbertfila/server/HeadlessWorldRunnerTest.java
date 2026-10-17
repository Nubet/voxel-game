package com.norbertfila.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HeadlessWorldRunnerTest {
    private final java.util.List<HeadlessWorldRunner> runners = new java.util.ArrayList<>();

    @AfterEach
    void closeRunners() {
        runners.forEach(HeadlessWorldRunner::close);
    }

    @Test
    void runsWorldCoreAndCommandsWithoutGraphicsRuntime() {
        HeadlessWorldRunner runner = runner();

        HeadlessWorldRunner.TickResult result = runner.tick(
                new ChunkPos(0, 0, 0), List.of(new HeadlessWorldRunner.SetBlock(new BlockPos(1, 1, 1), BlockId.AIR)));

        assertEquals(1, result.tick());
        assertTrue(result.generatedChunks() > 0);
        assertEquals(1, result.changedBlocks());
        assertEquals(BlockId.AIR, runner.world().getBlock(new BlockPos(1, 1, 1)).orElseThrow());
    }

    @Test
    void sameSeedAndCommandsProduceTheSameWorldResult() {
        HeadlessWorldRunner first = runner();
        HeadlessWorldRunner second = runner();
        List<HeadlessWorldRunner.Command> commands =
                List.of(new HeadlessWorldRunner.SetBlock(new BlockPos(-1, 2, -1), BlockId.DIRT));

        first.tick(new ChunkPos(0, 0, 0), commands);
        second.tick(new ChunkPos(0, 0, 0), commands);

        assertEquals(
                first.world().getBlock(new BlockPos(-1, 2, -1)), second.world().getBlock(new BlockPos(-1, 2, -1)));
        assertEquals(first.world().residentPositions(), second.world().residentPositions());
    }

    @Test
    void rejectsAccessFromAnotherThread() throws Exception {
        HeadlessWorldRunner runner = runner();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                runner.tick(new ChunkPos(0, 0, 0), List.of());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        thread.start();
        thread.join();

        assertTrue(failure.get() instanceof IllegalStateException);
        assertEquals(0, runner.tickCount());
    }

    private HeadlessWorldRunner runner() {
        var runner =
                new HeadlessWorldRunner(new HillsGenerator(123), new ViewDemand(1, 0, 0, ViewDemand.Shape.SQUARE), 8);
        runners.add(runner);
        return runner;
    }
}
