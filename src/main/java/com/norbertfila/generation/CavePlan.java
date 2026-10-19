package com.norbertfila.generation;

import com.norbertfila.world.Chunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CavePlan {
    private final Map<Long, List<Segment>> columns;
    private final List<Segment> segments;
    private final List<CaveSystem> systems;

    CavePlan(List<Segment> segments) {
        this(segments, List.of());
    }

    CavePlan(List<Segment> segments, List<CaveSystem> systems) {
        this.segments = List.copyOf(segments);
        this.systems = List.copyOf(systems);
        Map<Long, List<Segment>> index = new HashMap<>();
        for (Segment segment : segments) {
            int minX = chunk(segment.x() - segment.boundRadiusXZ());
            int maxX = chunk(segment.x() + segment.boundRadiusXZ());
            int minZ = chunk(segment.z() - segment.boundRadiusXZ());
            int maxZ = chunk(segment.z() + segment.boundRadiusXZ());
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++)
                    index.computeIfAbsent(key(x, z), ignored -> new ArrayList<>())
                            .add(segment);
        }
        index.replaceAll((key, value) -> List.copyOf(value));
        columns = Map.copyOf(index);
    }

    List<Segment> segments(int chunkX, int chunkZ) {
        return columns.getOrDefault(key(chunkX, chunkZ), List.of());
    }

    List<Segment> allSegments() {
        return segments;
    }

    List<CaveSystem> systems() {
        return systems;
    }

    private static int chunk(double coordinate) {
        return (int) Math.floor(Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE) / Chunk.SIZE);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | Integer.toUnsignedLong(z);
    }

    record Segment(
            double x,
            double y,
            double z,
            double radiusX,
            double radiusY,
            double radiusZ,
            CaveProfile profile,
            double roughness) {
        Segment(double x, double y, double z, double radiusX, double radiusY, CaveProfile profile, double roughness) {
            this(x, y, z, radiusX, radiusY, radiusX, profile, roughness);
        }

        Segment(double x, double y, double z, double radiusX, double radiusY) {
            this(x, y, z, radiusX, radiusY, CaveProfile.CLASSIC, 0);
        }

        Segment(double x, double y, double z, double radiusX, double radiusY, CaveProfile profile) {
            this(x, y, z, radiusX, radiusY, profile, profile == CaveProfile.SPAGHETTI ? .3 : .22);
        }

        Segment {
            Objects.requireNonNull(profile);
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || !Double.isFinite(radiusX)
                    || !Double.isFinite(radiusY)
                    || radiusX <= 0
                    || radiusX > 8
                    || radiusY <= 0
                    || radiusY > 8
                    || !Double.isFinite(radiusZ)
                    || radiusZ <= 0
                    || radiusZ > 8
                    || !Double.isFinite(roughness)
                    || roughness < 0
                    || roughness > .65) throw new IllegalArgumentException("Invalid cave ellipsoid");
        }

        double boundRadiusXZ() {
            return Math.max(radiusX, radiusZ) + roughness;
        }

        double boundRadiusY() {
            return radiusY + roughness;
        }

        boolean coreContains(double px, double py, double pz) {
            return profile != CaveProfile.CHAMBER
                    && roughness > 0
                    && Math.abs(px - x) <= .85
                    && Math.abs(pz - z) <= .85
                    && Math.abs(py - y) <= 1.55;
        }

        boolean contains(double px, double py, double pz) {
            double nx = (px - x) / radiusX;
            double ny = (py - y) / radiusY;
            double nz = (pz - z) / radiusZ;
            return nx * nx + ny * ny + nz * nz < 1;
        }
    }
}
