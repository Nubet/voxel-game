package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.RegionPos;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RegionIndex {
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x);
    private static final Comparator<RegionPos> REGION_ORDER =
            Comparator.comparingInt(RegionPos::y).thenComparingInt(RegionPos::z).thenComparingInt(RegionPos::x);

    private final int regionEdge;
    private final Map<RegionPos, Set<ChunkPos>> chunksByRegion = new HashMap<>();

    public RegionIndex(int regionEdge) {
        if (regionEdge < 1) throw new IllegalArgumentException("Region edge must be positive");
        this.regionEdge = regionEdge;
    }

    public boolean add(ChunkPos chunk) {
        Objects.requireNonNull(chunk);
        return chunksByRegion
                .computeIfAbsent(RegionPos.fromChunk(chunk, regionEdge), ignored -> new HashSet<>())
                .add(chunk);
    }

    public boolean remove(ChunkPos chunk) {
        Objects.requireNonNull(chunk);
        RegionPos region = RegionPos.fromChunk(chunk, regionEdge);
        Set<ChunkPos> chunks = chunksByRegion.get(region);
        if (chunks == null || !chunks.remove(chunk)) return false;
        if (chunks.isEmpty()) chunksByRegion.remove(region);
        return true;
    }

    public boolean contains(ChunkPos chunk) {
        Objects.requireNonNull(chunk);
        Set<ChunkPos> chunks = chunksByRegion.get(RegionPos.fromChunk(chunk, regionEdge));
        return chunks != null && chunks.contains(chunk);
    }

    public List<ChunkPos> chunks(RegionPos region) {
        Set<ChunkPos> chunks = chunksByRegion.get(region);
        if (chunks == null) return List.of();
        return chunks.stream().sorted(CHUNK_ORDER).toList();
    }

    public List<RegionPos> regions() {
        return chunksByRegion.keySet().stream().sorted(REGION_ORDER).toList();
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
        int count = 0;
        for (Set<ChunkPos> chunks : chunksByRegion.values()) count += chunks.size();
        return count;
    }
}
