package com.norbertfila.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.chat.ChatLink;
import com.norbertfila.generation.*;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.ChunkPos;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class LocationSearchTest {
    @Test
    void biomeCoordinatesSuggestTeleportAboveTheMatchedColumnForExactAndLimitedResults() {
        for (var status : new BiomeLocator.Status[] {BiomeLocator.Status.FOUND, BiomeLocator.Status.LIMIT_REACHED}) {
            var reply = LocationSearch.reply(new BiomeLocator.Result(
                    BiomeType.FOREST, status, Optional.of(new BiomeLocator.Match(-10, 20, 70, 22)), 100));
            assertFalse(reply.error());
            assertEquals(
                    "X=-10 Z=20",
                    reply.text().substring(reply.link().start(), reply.link().end()));
            var suggestion =
                    assertInstanceOf(ChatLink.SuggestCommand.class, reply.link().action());
            assertEquals("/tp -9.5 71 20.5", suggestion.command());
        }
        var missing = LocationSearch.reply(
                new BiomeLocator.Result(BiomeType.FOREST, BiomeLocator.Status.NOT_FOUND, Optional.empty(), 100));
        assertTrue(missing.error());
        assertNull(missing.link());
    }

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
        var searches = new LocationSearch(terrain);
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
        var searches = new LocationSearch(terrain);
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
        var reply = LocationSearch.reply(new BiomeLocator.Result(
                BiomeType.FOREST,
                BiomeLocator.Status.LIMIT_REACHED,
                Optional.of(new BiomeLocator.Match(10, 20, 70, 22)),
                100));
        assertTrue(reply.text().contains("Nearest not confirmed"));
        assertTrue(reply.text().contains("X=10 Z=20"));
        assertTrue(LocationSearch.reply(new BiomeLocator.Result(
                        BiomeType.FOREST, BiomeLocator.Status.LIMIT_REACHED, Optional.empty(), 100))
                .text()
                .contains("incomplete"));
    }

    @Test
    void riftsShareTheSearchWorkerAndReportRealCoordinates() throws Exception {
        var origin = new PlayerState.Position(.5, 80, .5);
        var searches = new LocationSearch(new TerrainGenerator(42, TerrainConfig.defaults()));
        try (searches) {
            assertTrue(searches.requestRift(origin));
            assertFalse(searches.request(BiomeType.FOREST, origin));
            assertFalse(searches.requestRift(origin));
            GameCommands.Reply reply = null;
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (reply == null && System.nanoTime() < deadline) {
                reply = searches.poll().orElse(null);
                Thread.sleep(1);
            }
            assertNotNull(reply);
            assertFalse(reply.error(), reply.text());
            assertTrue(reply.text().startsWith("Rift at X="));
            assertTrue(reply.text().contains("Floor Y="));
        }
        assertFalse(searches.requestRift(origin));
        try (var unsupported = new LocationSearch(new HillsGenerator())) {
            assertFalse(unsupported.requestRift(origin));
        }
        assertTrue(LocationSearch.reply(new RiftLocator.Result(
                        RiftLocator.Status.LIMIT_REACHED, Optional.of(new RiftLocator.Match(10, 20, 40, 22))))
                .text()
                .contains("incomplete"));
        assertTrue(LocationSearch.reply(new RiftLocator.Result(RiftLocator.Status.NOT_FOUND, Optional.empty()))
                .error());
    }
}
