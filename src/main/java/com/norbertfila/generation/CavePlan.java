package com.norbertfila.generation;

import com.norbertfila.world.Chunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CavePlan {
    private final Map<Long, List<Segment>> columns;

    CavePlan(List<Segment> segments) {
        Map<Long, List<Segment>> index = new HashMap<>();
        for (Segment segment : segments) {
            int minX = chunk(segment.x() - segment.radiusXZ());
            int maxX = chunk(segment.x() + segment.radiusXZ());
            int minZ = chunk(segment.z() - segment.radiusXZ());
            int maxZ = chunk(segment.z() + segment.radiusXZ());
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

    private static int chunk(double coordinate) {
        return (int) Math.floor(Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE) / Chunk.SIZE);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | Integer.toUnsignedLong(z);
    }

    record Segment(double x, double y, double z, double radiusXZ, double radiusY) {
        Segment {
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || !Double.isFinite(radiusXZ)
                    || !Double.isFinite(radiusY)
                    || radiusXZ <= 0
                    || radiusXZ > 8
                    || radiusY <= 0
                    || radiusY > 8) throw new IllegalArgumentException("Invalid cave ellipsoid");
        }

        boolean contains(double px, double py, double pz) {
            double nx = (px - x) / radiusXZ;
            double ny = (py - y) / radiusY;
            double nz = (pz - z) / radiusXZ;
            return nx * nx + ny * ny + nz * nz < 1;
        }
    }
}
