package com.norbertfila.world;

public final class PlantRules {
    private PlantRules() {}

    public static boolean isPlant(byte id) {
        return switch (id) {
            case BlockId.SHORT_GRASS,
                    BlockId.FERN,
                    BlockId.DANDELION_YELLOW,
                    BlockId.DANDELION_WHITE,
                    BlockId.POPPY,
                    BlockId.CACTUS,
                    BlockId.DEAD_BUSH,
                    BlockId.REED,
                    BlockId.MUSHROOM,
                    BlockId.BERRY_BUSH,
                    BlockId.ALPINE_GRASS -> true;
            case BlockId.SNOW_LAYER -> true;
            default -> false;
        };
    }

    public static boolean supports(byte ground) {
        return ground == BlockId.GRASS
                || ground == BlockId.DIRT
                || ground == BlockId.PODZOL
                || ground == BlockId.MUD
                || ground == BlockId.COARSE_DIRT
                || ground == BlockId.DRY_GRASS
                || ground == BlockId.MOSS
                || ground == BlockId.ROOTED_DIRT
                || ground == BlockId.MYCELIUM;
    }

    public static boolean supportsDry(byte ground) {
        return ground == BlockId.SAND
                || ground == BlockId.RED_SAND
                || ground == BlockId.GRAVEL
                || ground == BlockId.STONE
                || ground == BlockId.TERRACOTTA_RED
                || ground == BlockId.TERRACOTTA_ORANGE
                || (Byte.toUnsignedInt(ground) >= Byte.toUnsignedInt(BlockId.TERRACOTTA)
                        && Byte.toUnsignedInt(ground) <= Byte.toUnsignedInt(BlockId.TERRACOTTA_BLACK))
                || ground == BlockId.COARSE_DIRT;
    }

    public static boolean supports(byte plant, byte ground, boolean nearWater) {
        return switch (plant) {
            case BlockId.SNOW_LAYER ->
                BlockRegistry.DEFAULT.occludesFullFace(ground)
                        || ground == BlockId.LEAVES
                        || ground == BlockId.PINE_NEEDLES
                        || ground == BlockId.SWAMP_LEAVES
                        || ground == BlockId.BIRCH_LEAVES
                        || ground == BlockId.DARK_OAK_LEAVES
                        || ground == BlockId.ACACIA_LEAVES
                        || ground == BlockId.JUNGLE_LEAVES
                        || ground == BlockId.AZALEA_LEAVES
                        || ground == BlockId.FLOWERING_AZALEA_LEAVES;
            case BlockId.CACTUS -> ground == BlockId.SAND || ground == BlockId.RED_SAND || ground == BlockId.CACTUS;
            case BlockId.DEAD_BUSH -> supportsDry(ground) || supports(ground);
            case BlockId.REED ->
                ground == BlockId.REED
                        || nearWater
                                && (supports(ground)
                                        || ground == BlockId.SAND
                                        || ground == BlockId.RED_SAND
                                        || ground == BlockId.CLAY);
            case BlockId.MUSHROOM -> supports(ground) || ground == BlockId.DIRT;
            case BlockId.ALPINE_GRASS -> supports(ground) || ground == BlockId.SNOW;
            default -> supports(ground);
        };
    }

    public static boolean canPlace(World world, BlockPos position) {
        return canPlace(world, position, BlockId.SHORT_GRASS);
    }

    public static boolean canPlace(World world, BlockPos position, byte plant) {
        if (position.y() == Integer.MIN_VALUE) return false;
        BlockPos below = new BlockPos(position.x(), position.y() - 1, position.z());
        return world.hasChunk(Coordinates.worldToChunk(below))
                && supports(plant, (byte) world.getBlock(below).orElseThrow(), nearWater(world, below));
    }

    static boolean unsupported(World world, BlockPos position) {
        if (position.y() == Integer.MIN_VALUE) return true;
        BlockPos below = new BlockPos(position.x(), position.y() - 1, position.z());
        if (world.getBlock(position).orElse(BlockId.AIR) == BlockId.REED) {
            for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                long x = (long) below.x() + d[0], z = (long) below.z() + d[1];
                if (x >= Integer.MIN_VALUE
                        && x <= Integer.MAX_VALUE
                        && z >= Integer.MIN_VALUE
                        && z <= Integer.MAX_VALUE
                        && world.getBlock(new BlockPos((int) x, below.y(), (int) z))
                                .isEmpty()) return false;
            }
        }
        return world.hasChunk(Coordinates.worldToChunk(below))
                && !supports(
                        (byte) world.getBlock(position).orElse(BlockId.AIR),
                        (byte) world.getBlock(below).orElseThrow(),
                        nearWater(world, below));
    }

    private static boolean nearWater(World world, BlockPos ground) {
        for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            long x = (long) ground.x() + d[0], z = (long) ground.z() + d[1];
            if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
                continue;
            if (world.getBlock(new BlockPos((int) x, ground.y(), (int) z)).orElse(BlockId.AIR) == BlockId.WATER)
                return true;
        }
        return false;
    }
}
