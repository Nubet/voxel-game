package com.norbertfila.meshing;

import com.norbertfila.world.BlockId;

public final class PackedVertex {
    public static final int BYTES = Integer.BYTES;
    private static final int FACE_SHIFT = 12;
    private static final int BLOCK_SHIFT = 15;
    private static final int CORNER_SHIFT = 23;

    private PackedVertex() {}

    public static int pack(int x, int y, int z, int face, byte blockId, int corner) {
        checkCoordinate(x, "x");
        checkCoordinate(y, "y");
        checkCoordinate(z, "z");
        if (face < 0 || face >= 6) throw new IllegalArgumentException("Invalid face: " + face);
        if (!BlockId.isValid(blockId) || blockId == BlockId.AIR) throw new IllegalArgumentException("Invalid block id");
        if (corner < 0 || corner >= 4) throw new IllegalArgumentException("Invalid face corner: " + corner);
        return x
                | y << 4
                | z << 8
                | face << FACE_SHIFT
                | Byte.toUnsignedInt(blockId) << BLOCK_SHIFT
                | corner << CORNER_SHIFT;
    }

    public static Decoded decode(int packed) {
        return new Decoded(
                packed & 0xf,
                packed >>> 4 & 0xf,
                packed >>> 8 & 0xf,
                packed >>> FACE_SHIFT & 0x7,
                (byte) (packed >>> BLOCK_SHIFT & 0xff),
                packed >>> CORNER_SHIFT & 0x3);
    }

    public static int[] cornerPosition(int face, int corner) {
        if (face < 0 || face >= VisibleFaceMesher.FACES.length)
            throw new IllegalArgumentException("Invalid face: " + face);
        if (corner < 0 || corner >= 4) throw new IllegalArgumentException("Invalid face corner: " + corner);
        return VisibleFaceMesher.FACES[face][corner].clone();
    }

    private static void checkCoordinate(int coordinate, String axis) {
        if (coordinate < 0 || coordinate > 15)
            throw new IllegalArgumentException(axis + " must be in [0, 16): " + coordinate);
    }

    public record Decoded(int x, int y, int z, int face, byte blockId, int corner) {}
}
