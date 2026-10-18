package com.norbertfila.commands;

import com.norbertfila.generation.BiomeLocator;
import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerState;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BiomeSearch implements AutoCloseable {
    public static final int RADIUS = 32768;
    private static final Logger LOG = Logger.getLogger(BiomeSearch.class.getName());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("biome-search").factory());
    private final BiomeLocator locator;
    private Future<BiomeLocator.Result> pending;
    private boolean closed;

    public BiomeSearch(TerrainSource terrain) {
        locator = new BiomeLocator(terrain);
    }

    public boolean request(BiomeType biome, PlayerState.Position origin) {
        if (closed
                || pending != null
                || origin.x() < Integer.MIN_VALUE
                || origin.x() > Integer.MAX_VALUE
                || origin.z() < Integer.MIN_VALUE
                || origin.z() > Integer.MAX_VALUE) return false;
        pending = worker.submit(() -> locator.locate(
                biome, origin.x(), origin.z(), RADIUS, System.nanoTime() + TimeUnit.SECONDS.toNanos(10)));
        return true;
    }

    public Optional<GameCommands.Reply> poll() {
        if (pending == null || !pending.isDone()) return Optional.empty();
        var completed = pending;
        pending = null;
        try {
            return Optional.of(reply(completed.get()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.of(new GameCommands.Reply("Biome search interrupted.", true));
        } catch (CancellationException cancelled) {
            return Optional.of(new GameCommands.Reply("Biome search cancelled.", true));
        } catch (ExecutionException failed) {
            LOG.log(Level.WARNING, "Biome search failed", failed.getCause());
            return Optional.of(new GameCommands.Reply("Biome search failed; try again.", true));
        }
    }

    static GameCommands.Reply reply(BiomeLocator.Result result) {
        String name = result.biome().name().toLowerCase(Locale.ROOT);
        if (result.match().isPresent()) {
            var match = result.match().orElseThrow();
            boolean nearest = result.status() == BiomeLocator.Status.FOUND;
            return new GameCommands.Reply(
                    (nearest ? "Nearest " : "Search limit reached; found ") + name
                            + " at X=" + match.x() + " Z=" + match.z()
                            + String.format(Locale.ROOT, " (%.1f blocks).", match.distance())
                            + (nearest ? "" : " Nearest not confirmed.")
                            + " Surface Y=" + match.surfaceY() + ".",
                    false);
        }
        return new GameCommands.Reply(
                result.status() == BiomeLocator.Status.NOT_FOUND
                        ? "No " + name + " within " + RADIUS + " blocks."
                        : "Search limit reached for " + name + "; no location found. Search is incomplete.",
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
