package com.norbertfila.world;

public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Aabb {
        if (!Double.isFinite(minX)
                || !Double.isFinite(minY)
                || !Double.isFinite(minZ)
                || !Double.isFinite(maxX)
                || !Double.isFinite(maxY)
                || !Double.isFinite(maxZ)
                || minX >= maxX
                || minY >= maxY
                || minZ >= maxZ) {
            throw new IllegalArgumentException("AABB must have finite bounds and positive volume");
        }
    }

    public Aabb move(double x, double y, double z) {
        return new Aabb(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    public Aabb swept(double x, double y, double z) {
        return new Aabb(
                minX + Math.min(0, x),
                minY + Math.min(0, y),
                minZ + Math.min(0, z),
                maxX + Math.max(0, x),
                maxY + Math.max(0, y),
                maxZ + Math.max(0, z));
    }

    public Aabb expandVertical(double below, double above) {
        if (!Double.isFinite(below) || !Double.isFinite(above) || below < 0 || above < 0) {
            throw new IllegalArgumentException("Vertical margin must be finite and non-negative");
        }
        return new Aabb(minX, minY - below, minZ, maxX, maxY + above, maxZ);
    }

    public boolean intersects(Aabb other) {
        return maxX > other.minX
                && minX < other.maxX
                && maxY > other.minY
                && minY < other.maxY
                && maxZ > other.minZ
                && minZ < other.maxZ;
    }
}
