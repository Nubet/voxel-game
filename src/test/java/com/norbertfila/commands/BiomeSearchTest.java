package com.norbertfila.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.ChunkPos;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class BiomeSearchTest {
    @Test
    void closesWithoutWaitingForSearchAndInterruptsTheWorker() throws Exception {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var terrain = new TerrainSource() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public int surfaceHeight(int x, int z) {
                return 0;
            }

            public ChunkData generate(ChunkPos pos) {
                throw new AssertionError();
            }

            public TerrainSummary sample(int x, int z) {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                    throw new AssertionError();
                } catch (InterruptedException cancelled) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                    throw new CancellationException();
                }
            }
        };
        var searches = new BiomeSearch(terrain);
        try {
            assertTrue(searches.request(BiomeType.FOREST, new PlayerState.Position(.5, 80, .5)));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
        } finally {
            searches.close();
        }
        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        assertTrue(searches.poll().isEmpty());
    }

    @Test
    void runsOneSearchAndRejectsRequestsAfterClose() throws Exception {
        var terrain = new HillsGenerator();
        var origin = new PlayerState.Position(.5, 80, .5);
        var biome = terrain.sample(0, 0).biome();
        var searches = new BiomeSearch(terrain);
        try (searches) {
            assertTrue(searches.request(biome, origin));
            assertFalse(searches.request(biome, origin));
            GameCommands.Reply reply = null;
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (reply == null && System.nanoTime() < deadline) {
                reply = searches.poll().orElse(null);
                Thread.sleep(1);
            }
            assertNotNull(reply);
            assertFalse(reply.error());
            assertTrue(reply.text().contains("Nearest"));
            assertTrue(reply.text().contains("X=0 Z=0"));
        }
        assertFalse(searches.request(biome, origin));
        assertTrue(searches.poll().isEmpty());
    }

    @Test
    void neverCallsBudgetLimitedCandidateNearest() {
        var reply = BiomeSearch.reply(new BiomeLocator.Result(
                BiomeType.FOREST,
                BiomeLocator.Status.LIMIT_REACHED,
                Optional.of(new BiomeLocator.Match(10, 20, 70, 22)),
                100));
        assertTrue(reply.text().contains("Nearest not confirmed"));
        assertTrue(reply.text().contains("X=10 Z=20"));
        assertTrue(BiomeSearch.reply(new BiomeLocator.Result(
                        BiomeType.FOREST, BiomeLocator.Status.LIMIT_REACHED, Optional.empty(), 100))
                .text()
                .contains("incomplete"));
    }
}
