package com.norbertfila.world;

public final class PlantRules {
    private PlantRules() {}

    public static boolean isPlant(byte id) {
        return switch (id) {
            case BlockId.SHORT_GRASS, BlockId.FERN, BlockId.DANDELION_YELLOW, BlockId.DANDELION_WHITE, BlockId.POPPY ->
                true;
            default -> false;
        };
    }

    public static boolean supports(byte ground) {
        return ground == BlockId.GRASS;
    }

    public static boolean supportsDry(byte ground) {
        return ground == BlockId.SAND || ground == BlockId.GRAVEL || ground == BlockId.STONE;
    }

    public static boolean canPlace(World world, BlockPos position) {
        if (position.y() == Integer.MIN_VALUE) return false;
        BlockPos below = new BlockPos(position.x(), position.y() - 1, position.z());
        return world.hasChunk(Coordinates.worldToChunk(below))
                && supports((byte) world.getBlock(below).orElseThrow());
    }

    static boolean unsupported(World world, BlockPos position) {
        if (position.y() == Integer.MIN_VALUE) return true;
        BlockPos below = new BlockPos(position.x(), position.y() - 1, position.z());
        return world.hasChunk(Coordinates.worldToChunk(below))
                && !supports((byte) world.getBlock(below).orElseThrow());
    }
}
