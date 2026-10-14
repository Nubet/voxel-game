package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.util.Objects;

public final class ChunkEntry {
    private final ChunkPos position;
    private final long requestedAt = System.nanoTime();
    private boolean everUploaded;
    private ChunkState state = ChunkState.REQUESTED;
    private long chunkRevision = -1;
    private long meshRevision = -1;
    private long generationToken = -1;
    private boolean gpuResident;

    public ChunkEntry(ChunkPos position) {
        this.position = Objects.requireNonNull(position);
    }

    public ChunkPos position() {
        return position;
    }

    public long requestedAt() {
        return requestedAt;
    }

    public ChunkState state() {
        return state;
    }

    public long chunkRevision() {
        return chunkRevision;
    }

    public long meshRevision() {
        return meshRevision;
    }

    public long generationToken() {
        return generationToken;
    }

    public void setGenerationToken(long token) {
        if (token < 0) throw new IllegalArgumentException("Generation token must not be negative");
        generationToken = token;
    }

    public boolean isCurrent(long revision, long token) {
        return chunkRevision == revision && generationToken == token;
    }

    public void beginGenerating() {
        transitionTo(ChunkState.GENERATING);
    }

    public void generated(long revision) {
        if (revision < 0) throw new IllegalArgumentException("Chunk revision must not be negative");
        transitionTo(ChunkState.GENERATED);
        chunkRevision = revision;
    }

    public boolean beginMeshing(long revision) {
        if (revision < chunkRevision || (state != ChunkState.GENERATED && state != ChunkState.RESIDENT_GPU))
            return false;
        chunkRevision = revision;
        transitionTo(ChunkState.MESHING);
        return true;
    }

    public boolean readyCpu(long revision) {
        if (revision != chunkRevision || state != ChunkState.MESHING) return false;
        meshRevision++;
        transitionTo(ChunkState.READY_CPU);
        return true;
    }

    public boolean uploaded(long revision) {
        if (revision != meshRevision || state != ChunkState.READY_CPU) return false;
        transitionTo(ChunkState.RESIDENT_GPU);
        gpuResident = true;
        return true;
    }

    /** Returns true exactly once, for the first successful upload of this residency. */
    public boolean firstUpload() {
        if (!gpuResident || everUploaded) return false;
        everUploaded = true;
        return true;
    }

    public boolean meshFailed() {
        if (state != ChunkState.MESHING && state != ChunkState.READY_CPU) return false;
        transitionTo(gpuResident ? ChunkState.RESIDENT_GPU : ChunkState.GENERATED);
        return true;
    }

    public void beginEviction() {
        transitionTo(ChunkState.EVICTING);
    }

    public void transitionTo(ChunkState next) {
        Objects.requireNonNull(next);
        if (!isAllowed(state, next)) {
            throw new IllegalStateException("Invalid chunk transition: " + state + " -> " + next);
        }
        state = next;
    }

    private static boolean isAllowed(ChunkState current, ChunkState next) {
        return switch (current) {
            case REQUESTED -> next == ChunkState.GENERATING || next == ChunkState.EVICTING;
            case GENERATING -> next == ChunkState.GENERATED;
            case GENERATED -> next == ChunkState.MESHING || next == ChunkState.EVICTING;
            case MESHING ->
                next == ChunkState.READY_CPU || next == ChunkState.GENERATED || next == ChunkState.RESIDENT_GPU;
            case READY_CPU ->
                next == ChunkState.RESIDENT_GPU || next == ChunkState.GENERATED || next == ChunkState.EVICTING;
            case RESIDENT_GPU -> next == ChunkState.MESHING || next == ChunkState.EVICTING;
            case EVICTING -> false;
        };
    }
}
