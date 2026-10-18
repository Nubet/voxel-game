package com.norbertfila.generation;

record CaveConfig(int minLength, int maxLength, double minRadius, double maxRadius, double regionScale) {
    CaveConfig {
        if (minLength < 20
                || maxLength < minLength
                || maxLength > 90
                || !Double.isFinite(minRadius)
                || !Double.isFinite(maxRadius)
                || minRadius < 1.3
                || maxRadius < minRadius
                || maxRadius > 2.5
                || !Double.isFinite(regionScale)
                || regionScale < 256) throw new IllegalArgumentException("Invalid cave configuration");
    }

    static CaveConfig defaults() {
        return new CaveConfig(20, 90, 1.3, 2.5, 768);
    }
}
