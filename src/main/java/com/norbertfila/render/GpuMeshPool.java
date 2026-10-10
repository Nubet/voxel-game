package com.norbertfila.render;

import com.norbertfila.world.ChunkPos;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GpuMeshPool implements AutoCloseable {
    private final GpuMemoryBudget budget;
    private final Map<ChunkPos, Allocation> active = new HashMap<>();
    private final ArrayDeque<Allocation> pendingDeletion = new ArrayDeque<>();

    public GpuMeshPool(GpuMemoryBudget budget) {
        this.budget = Objects.requireNonNull(budget);
    }

    public void put(ChunkPos position, GpuMesh mesh) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(mesh);
        long bytes = bytes(mesh);
        if (!budget.tryReserve(bytes)) {
            throw new IllegalStateException("GPU memory budget exceeded: " + bytes + " bytes requested");
        }
        Allocation previous = active.put(position, new Allocation(mesh, bytes));
        if (previous != null) pendingDeletion.addLast(previous);
    }

    public GpuMesh get(ChunkPos position) {
        Allocation allocation = active.get(position);
        return allocation == null ? null : allocation.mesh();
    }

    public GpuMesh remove(ChunkPos position) {
        Allocation allocation = active.remove(Objects.requireNonNull(position));
        if (allocation == null) return null;
        pendingDeletion.addLast(allocation);
        return allocation.mesh();
    }

    public DeletionStats drainDeletions(int maximum) {
        if (maximum <= 0) throw new IllegalArgumentException("Maximum deletions must be positive");
        int count = 0;
        long bytes = 0;
        while (count < maximum && !pendingDeletion.isEmpty()) {
            Allocation allocation = pendingDeletion.removeFirst();
            allocation.mesh().close();
            budget.release(allocation.bytes());
            bytes += allocation.bytes();
            count++;
        }
        return new DeletionStats(count, bytes, pendingDeletion.size());
    }

    public int size() {
        return active.size();
    }

    public int pendingDeletionCount() {
        return pendingDeletion.size();
    }

    public long allocatedBytes() {
        return budget.allocatedBytes();
    }

    public long fragmentationBytes() {
        long bytes = 0;
        for (Allocation allocation : pendingDeletion) bytes += allocation.bytes();
        return bytes;
    }

    public GpuMemoryBudget budget() {
        return budget;
    }

    @Override
    public void close() {
        for (Allocation allocation : active.values()) {
            allocation.mesh().close();
            budget.release(allocation.bytes());
        }
        active.clear();
        while (!pendingDeletion.isEmpty()) {
            Allocation allocation = pendingDeletion.removeFirst();
            allocation.mesh().close();
            budget.release(allocation.bytes());
        }
    }

    private static long bytes(GpuMesh mesh) {
        return (long) mesh.vertexCount() * mesh.format().stride() * Float.BYTES;
    }

    private record Allocation(GpuMesh mesh, long bytes) {}

    public record DeletionStats(int count, long bytes, int remaining) {}
}
