package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.TerrainSummary;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FarTerrainStreamerTest {
    @Test
    void disablingFarCancelsQueuedWorkAndInterruptsTheRunningBuild() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        TerrainSource source = new FlatSource() {
            @Override
            public TerrainSummary sample(int x, int z) {
                started.countDown();
                try {
                    new java.util.concurrent.CountDownLatch(1).await();
                } catch (InterruptedException stopped) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return super.sample(x, z);
            }
        };
        try (var streamer = new FarTerrainStreamer(source, overlay, 1)) {
            streamer.update(8, 8, 2, 16);
            streamer.process();
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
            streamer.update(8, 8, 2, 0);
            assertTrue(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, streamer.metrics().inFlight());
            streamer.process();
            assertTrue(streamer.residency().tiles().isEmpty());
        }
    }

    private static final int SURFACE = 10;
    private final WorldChangeOverlay overlay = new WorldChangeOverlay();

    @Test
    void publishesEveryTileWithinTheFarRadiusIncludingUnderNearTerrain() throws Exception {
        try (var streamer = new FarTerrainStreamer(new FlatSource(), overlay, 2)) {
            streamer.update(8, 8, 4, 40);
            settle(streamer);

            var tiles = streamer.residency().tiles();
            assertEquals(1, tiles.get(new FarTilePos(0, 0)).sampleStep(), "fills near columns until they load");
            assertTrue(tiles.containsKey(new FarTilePos(1, 0)));
            assertEquals(1, tiles.get(new FarTilePos(1, 0)).sampleStep());
            assertEquals(2, tiles.get(new FarTilePos(-8, 0)).sampleStep(), "about 490 blocks away");
            assertEquals(4, tiles.get(new FarTilePos(-9, 0)).sampleStep(), "about 550 blocks away");
            for (FarTileMesh mesh : tiles.values()) {
                double dx = (mesh.tileX() + .5) * 64 - 8, dz = (mesh.tileZ() + .5) * 64 - 8;
                assertTrue(Math.hypot(dx, dz) <= 40 * 16 + 1);
            }
        }
    }

    @Test
    void editsRebuildTheirTileAndStaleResultsAreNotPublished() throws Exception {
        try (var streamer = new FarTerrainStreamer(new FlatSource(), overlay, 2)) {
            streamer.update(8, 8, 2, 16);
            // The edit lands after the tile's build was requested, so the first result is stale.
            streamer.process();
            overlay.set(new BlockPos(100, 90, 100), BlockId.STONE, FarTerrainTile.SIZE);
            streamer.markEdited(overlay.drainDirtyTiles());
            settle(streamer);

            FarTileMesh mesh = streamer.residency().tiles().get(new FarTilePos(1, 1));
            assertEquals(overlay.areaRevision(64, 64, 64), mesh.editRevision());
            assertTrue(mesh.maxY() >= 91);
        }
    }

    @Test
    void movingAwayDropsTilesAndKeepsBuildsBounded() throws Exception {
        CountingSource source = new CountingSource();
        try (var streamer = new FarTerrainStreamer(source, overlay, 1)) {
            streamer.update(8, 8, 2, 16);
            streamer.process();
            assertTrue(streamer.metrics().inFlight() <= 8);
            settle(streamer);
            long revision = streamer.residency().revision();

            streamer.update(100_000, 8, 2, 16);
            settle(streamer);

            assertTrue(streamer.residency().revision() > revision);
            for (FarTileMesh mesh : streamer.residency().tiles().values()) assertTrue(mesh.tileX() > 1000);
            assertEquals(0, source.chunks.get(), "far terrain never generates chunks");
        }
    }

    @Test
    void farDistanceNotAboveTheNearRadiusDisablesFarTerrain() throws Exception {
        try (var streamer = new FarTerrainStreamer(new FlatSource(), overlay, 1)) {
            streamer.update(8, 8, 8, 8);
            settle(streamer);
            assertTrue(streamer.residency().tiles().isEmpty());
        }
    }

    @Test
    void stepsChangeOnlyPastTheHysteresisMargin() {
        assertEquals(1, FarTerrainStreamer.step(250, null));
        assertEquals(2, FarTerrainStreamer.step(260, null));
        assertEquals(1, FarTerrainStreamer.step(270, 1), "within the margin");
        assertEquals(2, FarTerrainStreamer.step(300, 1));
        assertEquals(2, FarTerrainStreamer.step(240, 2));
        assertEquals(1, FarTerrainStreamer.step(220, 2));
        assertEquals(8, FarTerrainStreamer.step(5000, 4));
    }

    @Test
    void changingDistancesDropsInFlightResultsAndDisablesEveryTileEvenAtItsCenter() throws Exception {
        try (var streamer = new FarTerrainStreamer(new FlatSource(), overlay, 2)) {
            streamer.update(32, 32, 4, 32);
            streamer.process();
            assertTrue(streamer.metrics().inFlight() > 0);
            streamer.update(32, 32, 4, 0);
            settle(streamer);
            assertTrue(streamer.residency().tiles().isEmpty(), "Off must hide the tile under the camera too");
            assertEquals(0, streamer.metrics().desired());
            streamer.update(32, 32, 4, 32);
            settle(streamer);
            assertTrue(streamer.residency().tiles().size() > 1);
            streamer.update(32, 32, 4, 8);
            settle(streamer);
            for (var mesh : streamer.residency().tiles().values())
                assertTrue(Math.hypot(mesh.tileX() * 64, mesh.tileZ() * 64) <= 8 * 16);
            streamer.update(32, 32, 8, 8);
            settle(streamer);
            assertTrue(streamer.residency().tiles().isEmpty());
        }
    }

    private static void settle(FarTerrainStreamer streamer) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        do {
            streamer.process();
            var metrics = streamer.metrics();
            if (metrics.inFlight() == 0 && metrics.queued() == 0) return;
            Thread.sleep(2);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Far terrain did not settle");
    }

    private static class FlatSource implements TerrainSource {
        @Override
        public int surfaceHeight(int worldX, int worldZ) {
            return SURFACE;
        }

        @Override
        public TerrainSummary sample(int worldX, int worldZ) {
            return new TerrainSummary(SURFACE, BlockId.GRASS, BlockId.DIRT, BlockId.STONE, 0);
        }

        @Override
        public long seed() {
            return 0;
        }

        @Override
        public int generatorVersion() {
            return 0;
        }

        @Override
        public ChunkData generate(ChunkPos position) {
            throw new AssertionError("far terrain must not generate chunks");
        }
    }

    private static final class CountingSource extends FlatSource {
        private final AtomicInteger chunks = new AtomicInteger();

        @Override
        public ChunkData generate(ChunkPos position) {
            chunks.incrementAndGet();
            return super.generate(position);
        }
    }
}
