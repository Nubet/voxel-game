package com.norbertfila;

import com.norbertfila.core.Game;
import com.norbertfila.core.GameBenchmark;
import com.norbertfila.diagnostics.DiagnosticsLog;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Main {
    public static void main(String[] args) {
        DiagnosticsLog diagnostics = DiagnosticsLog.install();
        try {
            if (GameBenchmark.requested()) new GameBenchmark().run();
            else new Game().run();
        } catch (Throwable failure) {
            Logger.getLogger("voxel").log(Level.SEVERE, "Game terminated with an uncaught failure", failure);
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Game terminated with a checked failure", failure);
        } finally {
            diagnostics.close();
        }
    }
}
