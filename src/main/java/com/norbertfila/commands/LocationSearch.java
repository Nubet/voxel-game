package com.norbertfila.commands;

import com.norbertfila.chat.ChatLink;
import com.norbertfila.generation.BiomeLocator;
import com.norbertfila.generation.RiftLocator;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerState;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LocationSearch implements AutoCloseable {
    public static final int RADIUS = 32768;
    private static final Logger LOG = Logger.getLogger(LocationSearch.class.getName());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("location-search").factory());
    private final BiomeLocator locator;
    private final RiftLocator rifts;
    private Future<GameCommands.Reply> pending;
    private boolean closed;

    public LocationSearch(TerrainSource terrain) {
        locator = new BiomeLocator(terrain);
        rifts = terrain instanceof TerrainGenerator generator ? new RiftLocator(generator) : null;
    }

    public boolean request(BiomeType biome, PlayerState.Position origin) {
        if (unavailable(origin)) return false;
        pending = worker.submit(() -> reply(locator.locate(
                biome, origin.x(), origin.z(), RADIUS, System.nanoTime() + TimeUnit.SECONDS.toNanos(10))));
        return true;
    }

    public boolean requestRift(PlayerState.Position origin) {
        if (rifts == null || unavailable(origin)) return false;
        pending = worker.submit(() ->
                reply(rifts.locate(origin.x(), origin.z(), RADIUS, System.nanoTime() + TimeUnit.SECONDS.toNanos(10))));
        return true;
    }

    private boolean unavailable(PlayerState.Position origin) {
        return closed
                || pending != null
                || !Double.isFinite(origin.x())
                || !Double.isFinite(origin.z())
                || origin.x() < Integer.MIN_VALUE
                || origin.x() > Integer.MAX_VALUE
                || origin.z() < Integer.MIN_VALUE
                || origin.z() > Integer.MAX_VALUE;
    }

    public Optional<GameCommands.Reply> poll() {
        if (pending == null || !pending.isDone()) return Optional.empty();
        var completed = pending;
        pending = null;
        try {
            return Optional.of(completed.get());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.of(new GameCommands.Reply("Location search interrupted.", true));
        } catch (CancellationException cancelled) {
            return Optional.of(new GameCommands.Reply("Location search cancelled.", true));
        } catch (ExecutionException failed) {
            LOG.log(Level.WARNING, "Location search failed", failed.getCause());
            return Optional.of(new GameCommands.Reply("Location search failed; try again.", true));
        }
    }

    static GameCommands.Reply reply(BiomeLocator.Result result) {
        String name = result.biome().name().toLowerCase(Locale.ROOT);
        if (result.match().isPresent()) {
            var match = result.match().orElseThrow();
            boolean nearest = result.status() == BiomeLocator.Status.FOUND;
            String prefix = (nearest ? "Nearest " : "Search limit reached; found ") + name + " at ";
            String coordinates = "X=" + match.x() + " Z=" + match.z();
            String command = "/tp " + (match.x() + .5) + " " + ((long) match.surfaceY() + 1) + " " + (match.z() + .5);
            return new GameCommands.Reply(
                    prefix + coordinates
                            + String.format(Locale.ROOT, " (%.1f blocks).", match.distance())
                            + (nearest ? "" : " Nearest not confirmed.")
                            + " Surface Y=" + match.surfaceY() + ".",
                    false,
                    new ChatLink(
                            prefix.length(),
                            prefix.length() + coordinates.length(),
                            new ChatLink.SuggestCommand(command)));
        }
        return new GameCommands.Reply(
                result.status() == BiomeLocator.Status.NOT_FOUND
                        ? "No " + name + " within " + RADIUS + " blocks."
                        : "Search limit reached for " + name + "; no location found. Search is incomplete.",
                true);
    }

    static GameCommands.Reply reply(RiftLocator.Result result) {
        if (result.match().isPresent()) {
            var match = result.match().orElseThrow();
            return new GameCommands.Reply(
                    "Rift at X=" + match.x() + " Z=" + match.z()
                            + String.format(
                                    Locale.ROOT, " (%.1f blocks). Floor Y=%d.", match.distance(), match.surfaceY())
                            + (result.status() == RiftLocator.Status.LIMIT_REACHED ? " Search incomplete." : ""),
                    false);
        }
        return new GameCommands.Reply(
                result.status() == RiftLocator.Status.NOT_FOUND
                        ? "No rift within " + RADIUS + " blocks."
                        : "Search limit reached for rift; no location found. Search is incomplete.",
                true);
    }

    @Override
    public void close() {
        closed = true;
        if (pending != null) pending.cancel(true);
        pending = null;
        worker.shutdownNow();
    }
}
