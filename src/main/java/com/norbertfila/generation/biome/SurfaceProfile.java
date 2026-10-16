package com.norbertfila.generation.biome;

import java.util.Objects;

public record SurfaceProfile(SurfaceMaterial surface, SurfaceMaterial subsurface) {
    public SurfaceProfile {
        Objects.requireNonNull(surface);
        Objects.requireNonNull(subsurface);
    }
}
