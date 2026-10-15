package com.norbertfila.lod;

import java.util.List;
import java.util.Objects;

public final class LodTransition {
    private LodTransition() {}

    public static LodLevel selectLevel(
            List<LodLevel> levels, LodLevel current, long distanceSquared, int hysteresisChunks) {
        Objects.requireNonNull(levels);
        if (levels.isEmpty() || hysteresisChunks < 0)
            throw new IllegalArgumentException("Invalid LOD transition configuration");
        LodLevel target = null;
        int targetIndex = -1;
        for (int index = 0; index < levels.size(); index++) {
            if (levels.get(index).contains(distanceSquared)) {
                target = levels.get(index);
                targetIndex = index;
                break;
            }
        }
        if (current == null) return target;
        int currentIndex = levels.indexOf(current);
        if (currentIndex < 0) throw new IllegalArgumentException("Current LOD level is not in levels");
        if (targetIndex < 0 || targetIndex == currentIndex) return targetIndex < 0 ? current : current;
        long hysteresis = hysteresisChunks;
        if (targetIndex > currentIndex) {
            long switchDistance = current.maxRadiusChunks() + hysteresis;
            return distanceSquared <= switchDistance * switchDistance ? current : target;
        }
        long switchDistance = Math.max(0, current.minRadiusChunks() - hysteresis);
        return distanceSquared >= switchDistance * switchDistance ? current : target;
    }
}
