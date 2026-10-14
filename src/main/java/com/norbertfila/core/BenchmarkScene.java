package com.norbertfila.core;

import com.norbertfila.generation.TerrainSource;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import java.util.function.Consumer;

/**
 * Deterministic game benchmark scenes. Every scene is a pure function of script time and the terrain sampled at a
 * fixed site, so repeated runs with the same seed follow the same camera path and issue the same edits.
 */
enum BenchmarkScene {
    STATIC(0, 10),
    ROTATE(0, 10) {
        @Override
        Pose pose(double t, Site site) {
            return new Pose(site.x(), site.surfaceY() + 12, site.z(), 45 + 90 * t, -15);
        }
    },
    EDITS(0, 10) {
        @Override
        Pose pose(double t, Site site) {
            return new Pose(site.x() - 6, site.surfaceY() + 8, site.z() - 6, 45, -35);
        }

        @Override
        void commands(double from, double to, Site site, Consumer<WorldRuntime.Action> sink) {
            // Toggle boxes straddling chunk borders on all three axes every 100 ms.
            for (long step = steps(from, .1); step < steps(to, .1); step++) {
                int corner = (int) (step % 4);
                boolean place = (step / 4) % 2 == 0;
                int baseX = site.chunkOriginX() + (corner & 1) * Chunk.SIZE - 2;
                int baseZ = site.chunkOriginZ() + (corner >> 1) * Chunk.SIZE - 2;
                int baseY = Math.floorDiv(site.surfaceY(), Chunk.SIZE) * Chunk.SIZE - 2;
                sink.accept(new WorldRuntime.Fill(
                        new BlockPos(baseX, baseY, baseZ),
                        new BlockPos(baseX + 3, baseY + 3, baseZ + 3),
                        place ? BlockId.STONE : BlockId.AIR));
            }
        }
    },
    WATER(2, 10) {
        @Override
        Pose pose(double t, Site site) {
            return new Pose(site.x() - 12, site.surfaceY() + 14, site.z() - 12, 45, -30);
        }

        @Override
        void commands(double from, double to, Site site, Consumer<WorldRuntime.Action> sink) {
            // Alternate a raised source pool so flow and recession keep the simulation busy.
            for (long step = steps(from, 3); step < steps(to, 3); step++) {
                boolean fill = step % 2 == 0;
                sink.accept(new WorldRuntime.Fill(
                        new BlockPos(site.x() - 2, site.surfaceY() + 4, site.z() - 2),
                        new BlockPos(site.x() + 2, site.surfaceY() + 4, site.z() + 2),
                        fill ? BlockId.WATER : BlockId.AIR));
            }
        }
    },
    ENCLOSED(1, 10) {
        @Override
        Pose pose(double t, Site site) {
            return new Pose(site.x(), site.surfaceY() - 22, site.z(), 30, 0);
        }

        @Override
        void commands(double from, double to, Site site, Consumer<WorldRuntime.Action> sink) {
            if (from <= 0 && to > 0)
                sink.accept(new WorldRuntime.Fill(
                        new BlockPos(site.x() - 4, site.surfaceY() - 24, site.z() - 4),
                        new BlockPos(site.x() + 4, site.surfaceY() - 20, site.z() + 4),
                        BlockId.AIR));
        }
    },
    SAVE(1, 10) {
        @Override
        Pose pose(double t, Site site) {
            return new Pose(site.x(), site.surfaceY() + 20, site.z(), 45, -30);
        }

        @Override
        void commands(double from, double to, Site site, Consumer<WorldRuntime.Action> sink) {
            // 49 slabs of 16x2x16 placed blocks: about 25k authoritative edits captured by every autosave.
            if (from > 0 || to <= 0) return;
            for (int cz = -3; cz <= 3; cz++)
                for (int cx = -3; cx <= 3; cx++) {
                    int x = site.chunkOriginX() + cx * Chunk.SIZE, z = site.chunkOriginZ() + cz * Chunk.SIZE;
                    sink.accept(new WorldRuntime.Fill(
                            new BlockPos(x, site.surfaceY() + 6, z),
                            new BlockPos(x + Chunk.SIZE - 1, site.surfaceY() + 7, z + Chunk.SIZE - 1),
                            BlockId.PLANKS));
                }
        }
    },
    FLY_NEW(0, 10) {
        @Override
        Pose pose(double t, Site site) {
            return new Pose(site.x() + FLIGHT_SPEED * t, site.flightY(), site.z(), 0, -10);
        }
    },
    /** Flies back over the terrain FLY_NEW streamed in, so it measures revisits only when run right after it. */
    FLY_RETURN(0, 10) {
        @Override
        Pose pose(double t, Site site) {
            return new Pose(site.x() + FLIGHT_SPEED * (FLIGHT_SECONDS - t), site.flightY(), site.z(), 180, -10);
        }
    };

    static final double FLIGHT_SPEED = 24;
    static final double FLIGHT_SECONDS = 10;
    private final double setupSeconds;
    private final double measureSeconds;

    BenchmarkScene(double setupSeconds, double measureSeconds) {
        this.setupSeconds = setupSeconds;
        this.measureSeconds = measureSeconds;
    }

    /** Unmeasured scripted time before measurement starts. */
    double setupSeconds() {
        return setupSeconds;
    }

    double measureSeconds() {
        return measureSeconds;
    }

    Pose pose(double t, Site site) {
        return new Pose(site.x(), site.surfaceY() + 12, site.z(), 45, -15);
    }

    /** Emits the commands scheduled in script time {@code [from, to)}. */
    void commands(double from, double to, Site site, Consumer<WorldRuntime.Action> sink) {}

    private static long steps(double t, double period) {
        return t < 0 ? 0 : (long) Math.ceil(t / period);
    }

    record Pose(double x, double y, double z, double yaw, double pitch) {}

    /** Terrain-derived anchor of a scene: a fixed column and a flight altitude clearing the flown path. */
    record Site(int x, int z, int surfaceY, int flightY) {
        static Site sample(TerrainSource terrain, int x, int z, double pathLength) {
            int highest = terrain.surfaceHeight(x, z);
            for (int offset = -64; offset <= pathLength + 64; offset += 8)
                highest = Math.max(highest, terrain.surfaceHeight(x + offset, z));
            return new Site(x, z, terrain.surfaceHeight(x, z), highest + 24);
        }

        int chunkOriginX() {
            return Math.floorDiv(x, Chunk.SIZE) * Chunk.SIZE;
        }

        int chunkOriginZ() {
            return Math.floorDiv(z, Chunk.SIZE) * Chunk.SIZE;
        }
    }
}
