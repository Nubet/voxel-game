package com.norbertfila.mob;

public enum SpawnZone {
    SURFACE,
    CAVE;

    /** Anything sky light reaches, even through a cave mouth, counts as surface. */
    public static SpawnZone fromSkyLight(int skyLight) {
        return skyLight > 0 ? SURFACE : CAVE;
    }
}
