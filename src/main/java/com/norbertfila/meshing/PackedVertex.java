package com.norbertfila.meshing;

import com.norbertfila.world.BlockId;

public final class PackedVertex {
    public static final int BYTES = Integer.BYTES;
    private static final int FACE_SHIFT = 12;
    private static final int BLOCK_SHIFT = 15;
    private static final int CORNER_SHIFT = 23;
    private static final int TEXTURE_LAYER_SHIFT = 25;
    // shortcut: packed layers stop at 127; widen the vertex format when the registry exceeds 128 layers.
    private static final int TEXTURE_LAYER_MASK = 0x7f;

    private PackedVertex() {}

    public static int pack(int x, int y, int z, int face, byte blockId, int corner) {
        return pack(x, y, z, face, blockId, corner, 0);
    }

    public static int pack(int x, int y, int z, int face, byte blockId, int corner, int textureLayer) {
        checkCoordinate(x, "x");
        checkCoordinate(y, "y");
        checkCoordinate(z, "z");
        if (face < 0 || face >= 6) throw new IllegalArgumentException("Invalid face: " + face);
        if (!BlockId.isValid(blockId) || blockId == BlockId.AIR) throw new IllegalArgumentException("Invalid block id");
        if (corner < 0 || corner >= 4) throw new IllegalArgumentException("Invalid face corner: " + corner);
        if (textureLayer < 0 || textureLayer > TEXTURE_LAYER_MASK)
            throw new IllegalArgumentException("Texture layer must be in [0, 128): " + textureLayer);
        return x
                | y << 4
                | z << 8
                | face << FACE_SHIFT
                | Byte.toUnsignedInt(blockId) << BLOCK_SHIFT
                | corner << CORNER_SHIFT
                | textureLayer << TEXTURE_LAYER_SHIFT;
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

    public static int decodeTextureLayer(int packed) {
        return packed >>> TEXTURE_LAYER_SHIFT & TEXTURE_LAYER_MASK;
    }

    private static void checkCoordinate(int coordinate, String axis) {
        if (coordinate < 0 || coordinate > 15)
            throw new IllegalArgumentException(axis + " must be in [0, 16): " + coordinate);
    }

    public record Decoded(int x, int y, int z, int face, byte blockId, int corner) {}
}
