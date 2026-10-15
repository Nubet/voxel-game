package com.norbertfila.world;

public record ChunkPos(int x, int y, int z) {
    // The record hash gives neighboring chunks consecutive values, and immutable maps probe linearly, so lookups in a
    // residency snapshot of tens of thousands of chunks walked long runs of occupied slots.
    @Override
    public int hashCode() {
        long mixed = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        return (int) (mixed ^ mixed >>> 32);
    }
}
