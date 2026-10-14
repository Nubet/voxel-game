package com.norbertfila.core;

import com.norbertfila.generation.TerrainSource;
import com.norbertfila.meshing.ChunkMesher;
import com.norbertfila.persistence.world.WorldCatalog;
import com.norbertfila.persistence.world.WorldIo;
import com.norbertfila.streaming.ViewDemand;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongFunction;

public final class GameSession {
    public enum State {
        MENU,
        LOADING,
        PLAYING,
        CANCELLING,
        SAVING,
        SAVE_ERROR
    }

    private final WorldIo io;
    private final LongFunction<TerrainSource> generators;
    private final ChunkMesher mesher;
    private final ViewDemand demand;
    private CompletableFuture<WorldCatalog.Loaded> loading;
    private WorldRuntime runtime;
    private State state = State.MENU;
    private Throwable failure;

    public GameSession(WorldIo io, LongFunction<TerrainSource> generators, ChunkMesher mesher, ViewDemand demand) {
        this.io = Objects.requireNonNull(io);
        this.generators = Objects.requireNonNull(generators);
        this.mesher = Objects.requireNonNull(mesher);
        this.demand = Objects.requireNonNull(demand);
    }

    public void open(WorldCatalog catalog, UUID id) {
        require(State.MENU);
        failure = null;
        loading = io.open(catalog, id);
        state = State.LOADING;
    }

    public void poll() {
        if (loading != null && loading.isDone()) {
            try {
                var loaded = loading.join();
                if (state != State.CANCELLING)
                    runtime = new WorldRuntime(
                            generators.apply(loaded.snapshot().identity().seed()),
                            mesher,
                            demand,
                            loaded.statePath(),
                            io,
                            loaded.snapshot());
            } catch (CompletionException failed) {
                failure = failed.getCause();
            } catch (RuntimeException failed) {
                failure = failed;
            }
            loading = null;
            if (runtime == null) state = State.MENU;
        }
        if (runtime == null) return;
        if (runtime.isStopped()) {
            try {
                runtime.close();
            } catch (RuntimeException failed) {
                failure = failed;
            }
            runtime = null;
            state = State.MENU;
        } else if (runtime.state() == WorldRuntime.State.SAVE_ERROR) {
            failure = runtime.saveFailure();
            state = State.SAVE_ERROR;
        }
    }

    public boolean activate() {
        require(State.LOADING);
        if (runtime == null || !runtime.activate()) return false;
        state = State.PLAYING;
        return true;
    }

    public void cancelLoading() {
        require(State.LOADING);
        state = State.CANCELLING;
        if (runtime != null) runtime.requestStop();
    }

    public void saveAndQuit() {
        require(State.PLAYING);
        state = State.SAVING;
        runtime.requestStop();
    }

    public void retrySave() {
        require(State.SAVE_ERROR);
        runtime.retrySave();
        failure = null;
        state = State.SAVING;
    }

    public void discardChanges() {
        require(State.SAVE_ERROR);
        runtime.discardChanges();
        failure = null;
        state = State.SAVING;
    }

    public boolean isCurrent(WorldRuntime.SessionMesh mesh) {
        return (state == State.LOADING || state == State.PLAYING)
                && runtime != null
                && runtime.frame() != null
                && runtime.frame().isCurrent(mesh);
    }

    public State state() {
        return state;
    }

    public WorldRuntime runtime() {
        return runtime;
    }

    public Throwable failure() {
        return failure;
    }

    private void require(State expected) {
        if (state != expected) throw new IllegalStateException("Expected " + expected + ", got " + state);
    }
}
