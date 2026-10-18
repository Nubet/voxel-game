package com.norbertfila.diagnostics;

import com.norbertfila.fluid.FluidSimulator;
import com.norbertfila.lod.FarTerrainStreamer;
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
        FluidSimulator.Metrics fluid,
        ChunkManager.Metrics streaming,
        SaveScheduler.Metrics save,
        int outstandingAcks,
        int publishedMeshes,
        int queuedActions,
        long oldestAckNanos,
        boolean movementWaiting,
        long estimatedChunkBytes,
        long streamingNanos,
        long publishNanos,
        FarTerrainStreamer.Metrics far,
        long outstandingMeshBytes) {}
