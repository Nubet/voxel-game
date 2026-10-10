package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.util.Objects;

public record ChunkPriority(long distanceSquared, double directionBonus) {
    public ChunkPriority {
        if (distanceSquared < 0 || !Double.isFinite(directionBonus)) {
            throw new IllegalArgumentException("Invalid chunk priority");
        }
    }

    public static ChunkPriority forPosition(ChunkPos position, ChunkPos center, double directionX, double directionZ) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(center);
        int dx = position.x() - center.x();
        int dz = position.z() - center.z();
        long distanceSquared = (long) dx * dx + (long) dz * dz;
        double distance = Math.sqrt(distanceSquared);
        double directionLength = Math.hypot(directionX, directionZ);
        if (!Double.isFinite(directionLength)) throw new IllegalArgumentException("Direction must be finite");
        double bonus = directionLength == 0.0 || distance == 0.0
                ? 0.0
                : Math.max(0.0, (dx * directionX + dz * directionZ) / (distance * directionLength));
        return new ChunkPriority(distanceSquared, bonus);
    }

    static ChunkPriority unprioritized() {
        return new ChunkPriority(Long.MAX_VALUE, 0.0);
    }

    double score() {
        return distanceSquared - directionBonus;
    }
}
