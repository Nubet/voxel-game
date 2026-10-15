package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RegionPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chunks grouped into cubic regions for hierarchical culling. Regions and their chunk lists are kept up to date on
 * every add and remove, so a frame can walk them by index without sorting or allocating. Iteration order is
 * unspecified.
 */
public final class RegionIndex {
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x);
    private static final Comparator<RegionPos> REGION_ORDER =
            Comparator.comparingInt(RegionPos::y).thenComparingInt(RegionPos::z).thenComparingInt(RegionPos::x);

    private final int regionEdge;
    private final Map<RegionPos, Region> regionsByPosition = new HashMap<>();
    private final List<Region> regions = new ArrayList<>();
    private final Map<ChunkPos, Region> regionOfChunk = new HashMap<>();
    private int size;

    public RegionIndex(int regionEdge) {
        if (regionEdge < 1) throw new IllegalArgumentException("Region edge must be positive");
        this.regionEdge = regionEdge;
    }

    public boolean add(ChunkPos chunk) {
        Objects.requireNonNull(chunk);
        if (regionOfChunk.containsKey(chunk)) return false;
        RegionPos position = RegionPos.fromChunk(chunk, regionEdge);
        Region region = regionsByPosition.get(position);
        if (region == null) {
            region = new Region(position, position.bounds(regionEdge), regions.size());
            regionsByPosition.put(position, region);
            regions.add(region);
        }
        region.add(chunk);
        regionOfChunk.put(chunk, region);
        size++;
        return true;
    }

    public boolean remove(ChunkPos chunk) {
        Objects.requireNonNull(chunk);
        Region region = regionOfChunk.remove(chunk);
        if (region == null) return false;
        region.remove(chunk);
        size--;
        if (region.size() == 0) {
            regionsByPosition.remove(region.position);
            Region last = regions.remove(regions.size() - 1);
            if (last != region) {
                regions.set(region.slot, last);
                last.slot = region.slot;
            }
        }
        return true;
    }

    public boolean contains(ChunkPos chunk) {
        return regionOfChunk.containsKey(Objects.requireNonNull(chunk));
    }

    public int regionCount() {
        return regions.size();
    }

    public Region region(int index) {
        return regions.get(index);
    }

    /** Sorted copy for tests and tools; rendering walks {@link #region(int)} instead. */
    public List<ChunkPos> chunks(RegionPos position) {
        Region region = regionsByPosition.get(position);
        if (region == null) return List.of();
        return Arrays.stream(region.chunks, 0, region.size).sorted(CHUNK_ORDER).toList();
    }

    /** Sorted copy for tests and tools; rendering walks {@link #region(int)} instead. */
    public List<RegionPos> regions() {
        return regionsByPosition.keySet().stream().sorted(REGION_ORDER).toList();
    }

    public RegionPos regionOf(ChunkPos chunk) {
        return RegionPos.fromChunk(Objects.requireNonNull(chunk), regionEdge);
    }

    public RegionPos.Bounds bounds(RegionPos region) {
        return Objects.requireNonNull(region).bounds(regionEdge);
    }

    public int regionEdge() {
        return regionEdge;
    }

    public int size() {
        return size;
    }

    /** One region and its chunks; chunk order changes when chunks are removed. */
    public static final class Region {
        private final RegionPos position;
        private final RegionPos.Bounds bounds;
        private final Map<ChunkPos, Integer> slots = new HashMap<>();
        private ChunkPos[] chunks = new ChunkPos[16];
        private int size;
        private int slot;

        private Region(RegionPos position, RegionPos.Bounds bounds, int slot) {
            this.position = position;
            this.bounds = bounds;
            this.slot = slot;
        }

        private void add(ChunkPos chunk) {
            if (size == chunks.length) chunks = Arrays.copyOf(chunks, size * 2);
            slots.put(chunk, size);
            chunks[size++] = chunk;
        }

        private void remove(ChunkPos chunk) {
            int index = slots.remove(chunk);
            ChunkPos last = chunks[--size];
            chunks[size] = null;
            if (index != size) {
                chunks[index] = last;
                slots.put(last, index);
            }
        }

        public RegionPos position() {
            return position;
        }

        public RegionPos.Bounds bounds() {
            return bounds;
        }

        public int size() {
            return size;
        }

        public ChunkPos chunk(int index) {
            Objects.checkIndex(index, size);
            return chunks[index];
        }
    }
}
