package com.norbertfila.meshing;

import java.util.Objects;

/**
 * GPU-ready quad records for one mesh part. The vertex shader rebuilds six vertices per record from {@code
 * gl_VertexID}, and positions are local to the chunk, so the data does not depend on where the chunk is in the world.
 *
 * <p>Solid records ({@link Format#SOLID}, two ints):
 *
 * <ul>
 *   <li>word 0: bits 0-4, 5-9, 10-14 first corner x, y, z in 0..16; bits 15-17 face (0..5 cube faces in {@link
 *       VisibleFaceMesher#NORMALS} order, 6 and 7 the two cross-plant planes); bits 18-21 width - 1; bits 22-25
 *       height - 1; bit 26 back side of a cross plane.
 *   <li>word 1: bits 0-15 texture layer; bits 16-23 block ID.
 * </ul>
 *
 * <p>Water records ({@link Format#WATER}, four ints):
 *
 * <ul>
 *   <li>word 0: bits 0-3, 4-7, 8-11 cell x, y, z; bits 12-14 face; bit 15 falling water; bits 16-31 texture layer.
 *   <li>word 1: corner heights h00 (low 16 bits) and h10 (high 16 bits) as unsigned fractions of 65535.
 *   <li>word 2: corner heights h11 (low) and h01 (high).
 *   <li>word 3: surface flow x (low) and z (high) as signed fractions of 32767.
 * </ul>
 */
public record QuadMesh(Format format, int[] records, QuadRanges ranges) {
    public enum Format {
        SOLID(2),
        WATER(4);

        private final int ints;

        Format(int ints) {
            this.ints = ints;
        }

        public int ints() {
            return ints;
        }
    }

    public static final int VERTICES_PER_QUAD = 6;

    public QuadMesh {
        Objects.requireNonNull(format);
        Objects.requireNonNull(records);
        Objects.requireNonNull(ranges);
        if (records.length % format.ints != 0) throw new IllegalArgumentException("Incomplete quad records");
        if (ranges.total() != records.length / format.ints)
            throw new IllegalArgumentException("Layer ranges must cover every quad");
    }

    public static QuadMesh empty(Format format) {
        return new QuadMesh(format, new int[0], QuadRanges.EMPTY);
    }

    /** The records array itself, not a copy: published meshes are shared and must never be modified. */
    public int[] records() {
        return records;
    }

    public int quadCount() {
        return records.length / format.ints;
    }

    public long bytes() {
        return (long) records.length * Integer.BYTES;
    }
}
