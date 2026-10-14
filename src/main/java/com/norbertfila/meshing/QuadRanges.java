package com.norbertfila.meshing;

import com.norbertfila.world.RenderLayer;

/** Number of quads per render layer; quads are stored opaque first, then cutout, then translucent. */
public record QuadRanges(int opaque, int cutout, int translucent) {
    public static final QuadRanges EMPTY = new QuadRanges(0, 0, 0);

    public QuadRanges {
        if (opaque < 0 || cutout < 0 || translucent < 0)
            throw new IllegalArgumentException("Quad counts must not be negative");
    }

    public int first(RenderLayer layer) {
        return switch (layer) {
            case OPAQUE -> 0;
            case CUTOUT -> opaque;
            case TRANSLUCENT -> opaque + cutout;
        };
    }

    public int count(RenderLayer layer) {
        return switch (layer) {
            case OPAQUE -> opaque;
            case CUTOUT -> cutout;
            case TRANSLUCENT -> translucent;
        };
    }

    public int total() {
        return opaque + cutout + translucent;
    }
}
