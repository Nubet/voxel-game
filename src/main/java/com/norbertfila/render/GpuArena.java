package com.norbertfila.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class GpuArena {
    private final long capacityBytes;
    private final long alignmentBytes;
    private final TreeMap<Long, Long> free = new TreeMap<>();
    private final Map<Long, Allocation> active = new TreeMap<>();
    private long nextId;

    public GpuArena(long capacityBytes, long alignmentBytes) {
        if (capacityBytes <= 0 || alignmentBytes <= 0) {
            throw new IllegalArgumentException("Arena capacity and alignment must be positive");
        }
        this.capacityBytes = capacityBytes;
        this.alignmentBytes = alignmentBytes;
        free.put(0L, capacityBytes);
    }

    public Allocation allocate(long bytes) {
        if (bytes <= 0) throw new IllegalArgumentException("Allocation size must be positive");
        for (Map.Entry<Long, Long> entry : free.entrySet()) {
            // Read before removing: TreeMap may reuse a removed entry's node for its successor.
            long start = entry.getKey(), size = entry.getValue();
            long offset = align(start);
            long padding = offset - start;
            if (padding <= size && bytes <= size - padding) {
                long end = offset + bytes;
                free.remove(start);
                addFree(start, padding);
                addFree(end, size - padding - bytes);
                Allocation allocation = new Allocation(++nextId, offset, bytes);
                active.put(allocation.id(), allocation);
                return allocation;
            }
        }
        throw new IllegalStateException("GPU arena has no contiguous block for " + bytes + " bytes");
    }

    public void free(Allocation allocation) {
        Allocation current = active.remove(allocation.id());
        if (current != allocation) throw new IllegalArgumentException("Allocation does not belong to this arena");
        addFree(allocation.offset, allocation.size);
        mergeAround(allocation.offset);
    }

    public Compaction compact() {
        List<Allocation> allocations = new ArrayList<>(active.values());
        allocations.sort((left, right) -> Long.compare(left.offset, right.offset));
        long nextOffset = 0;
        long movedBytes = 0;
        List<Move> moves = new ArrayList<>();
        for (Allocation allocation : allocations) {
            long target = align(nextOffset);
            if (allocation.offset != target) {
                moves.add(new Move(allocation.id, allocation.offset, target, allocation.size));
                allocation.offset = target;
                movedBytes += allocation.size;
            }
            nextOffset = target + allocation.size;
        }
        free.clear();
        addFree(nextOffset, capacityBytes - nextOffset);
        return new Compaction(moves, movedBytes);
    }

    public long capacityBytes() {
        return capacityBytes;
    }

    public long alignmentBytes() {
        return alignmentBytes;
    }

    public long allocatedBytes() {
        long bytes = 0;
        for (Allocation allocation : active.values()) bytes += allocation.size;
        return bytes;
    }

    public long freeBytes() {
        long bytes = 0;
        for (long size : free.values()) bytes += size;
        return bytes;
    }

    public long largestFreeBlockBytes() {
        return free.isEmpty() ? 0 : Collections.max(free.values());
    }

    public long fragmentationBytes() {
        return freeBytes() - largestFreeBlockBytes();
    }

    public int allocationCount() {
        return active.size();
    }

    private long align(long offset) {
        long remainder = offset % alignmentBytes;
        return remainder == 0 ? offset : offset + alignmentBytes - remainder;
    }

    private void addFree(long offset, long size) {
        if (size > 0) free.put(offset, size);
    }

    private void mergeAround(long offset) {
        Map.Entry<Long, Long> current = free.floorEntry(offset);
        Map.Entry<Long, Long> next = free.higherEntry(current.getKey());
        if (next != null && current.getKey() + current.getValue() == next.getKey()) {
            free.put(current.getKey(), current.getValue() + next.getValue());
            free.remove(next.getKey());
        }
        current = free.floorEntry(offset);
        Map.Entry<Long, Long> previous = free.lowerEntry(current.getKey());
        if (previous != null && previous.getKey() + previous.getValue() == current.getKey()) {
            free.put(previous.getKey(), previous.getValue() + current.getValue());
            free.remove(current.getKey());
        }
    }

    public static final class Allocation {
        private final long id;
        private long offset;
        private final long size;

        private Allocation(long id, long offset, long size) {
            this.id = id;
            this.offset = offset;
            this.size = size;
        }

        public long id() {
            return id;
        }

        public long offset() {
            return offset;
        }

        public long size() {
            return size;
        }
    }

    public record Move(long id, long fromOffset, long toOffset, long size) {}

    public record Compaction(List<Move> moves, long movedBytes) {
        public Compaction {
            moves = List.copyOf(moves);
        }
    }
}
