package com.norbertfila.diagnostics;

import com.norbertfila.fluid.WaterSimulator;
import com.norbertfila.persistence.SaveScheduler;
import com.norbertfila.streaming.ChunkManager;

public record SimulationMetrics(
        long tickNanos,
        long predictionNanos,
        long waterNanos,
        long playerNanos,
        long contactNanos,
        long captureNanos,
        int waterProcessed,
        int waterChanged,
        int dirtyChunks,
        WaterSimulator.Metrics fluid,
        ChunkManager.Metrics streaming,
        SaveScheduler.Metrics save,
        int outstandingAcks,
        int publishedMeshes,
        int queuedActions,
        long oldestAckNanos,
        boolean movementWaiting,
        long estimatedChunkBytes) {}
