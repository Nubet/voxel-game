package com.norbertfila.lod;

import java.util.Objects;

public final class FarTerrainTile {
    private final int originX;
    private final int originZ;
    private final int tileSize;
    private final int sampleStep;
    private final int sampleCount;
    private final int[] heights;
    private final byte[] materials;

    public FarTerrainTile(int originX, int originZ, int tileSize, int sampleStep, int[] heights, byte[] materials) {
        if (tileSize < 1 || sampleStep < 1 || tileSize % sampleStep != 0)
            throw new IllegalArgumentException("Invalid far terrain tile sampling");
        Objects.requireNonNull(heights);
        Objects.requireNonNull(materials);
        int expectedSamples = tileSize / sampleStep + 1;
        if (heights.length != expectedSamples * expectedSamples || materials.length != heights.length) {
            throw new IllegalArgumentException("Invalid far terrain sample count");
        }
        this.originX = originX;
        this.originZ = originZ;
        this.tileSize = tileSize;
        this.sampleStep = sampleStep;
        this.sampleCount = expectedSamples;
        this.heights = heights.clone();
        this.materials = materials.clone();
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public int tileSize() {
        return tileSize;
    }

    public int sampleStep() {
        return sampleStep;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public int heightAt(int sampleX, int sampleZ) {
        return heights[index(sampleX, sampleZ)];
    }

    public byte materialAt(int sampleX, int sampleZ) {
        return materials[index(sampleX, sampleZ)];
    }

    public int triangleCount() {
        return (sampleCount - 1) * (sampleCount - 1) * 2;
    }

    public int vertexCount() {
        return triangleCount() * 3;
    }

    public long estimatedBytes() {
        return (long) heights.length * Integer.BYTES + materials.length;
    }

    private int index(int sampleX, int sampleZ) {
        if (sampleX < 0 || sampleX >= sampleCount || sampleZ < 0 || sampleZ >= sampleCount) {
            throw new IndexOutOfBoundsException("Far terrain sample outside tile");
        }
        return sampleZ * sampleCount + sampleX;
    }
}
