package com.norbertfila.world;

public final class PlantRules {
    private PlantRules() {}

    public static boolean isPlant(byte id) {
        if (Byte.toUnsignedInt(id) >= Byte.toUnsignedInt(BlockId.VINE_EAST)
                && Byte.toUnsignedInt(id) <= Byte.toUnsignedInt(BlockId.LARGE_FERN_TOP)) return true;
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
        if (attached(plant)) return attachmentSupported(world, position, plant, false);
        if (plant == BlockId.TALL_GRASS_TOP || plant == BlockId.LARGE_FERN_TOP) return false;
        if (plant == BlockId.TALL_GRASS || plant == BlockId.LARGE_FERN) {
            if (!WorldBounds.containsY(position.y() + 1)) return false;
            var upper = world.getBlock(new BlockPos(position.x(), position.y() + 1, position.z()));
            if (upper.isEmpty()
                    || !BlockRegistry.DEFAULT
                            .definition((byte) upper.getAsInt())
                            .replaceable()) return false;
        }
        if (position.y() == Integer.MIN_VALUE) return false;
        BlockPos below = new BlockPos(position.x(), position.y() - 1, position.z());
        return world.hasChunk(Coordinates.worldToChunk(below))
                && supports(plant, (byte) world.getBlock(below).orElseThrow(), nearWater(world, below));
    }

    static boolean unsupported(World world, BlockPos position) {
        byte plant = (byte) world.getBlock(position).orElse(BlockId.AIR);
        if (attached(plant)) return !attachmentSupported(world, position, plant, true);
        if (plant == BlockId.TALL_GRASS_TOP || plant == BlockId.LARGE_FERN_TOP) {
            var below = world.getBlock(new BlockPos(position.x(), position.y() - 1, position.z()));
            return below.isPresent()
                    && below.getAsInt()
                            != Byte.toUnsignedInt(
                                    plant == BlockId.TALL_GRASS_TOP ? BlockId.TALL_GRASS : BlockId.LARGE_FERN);
        }
        if (plant == BlockId.TALL_GRASS || plant == BlockId.LARGE_FERN) {
            var above = world.getBlock(new BlockPos(position.x(), position.y() + 1, position.z()));
            if (above.isPresent()
                    && above.getAsInt()
                            != Byte.toUnsignedInt(
                                    plant == BlockId.TALL_GRASS ? BlockId.TALL_GRASS_TOP : BlockId.LARGE_FERN_TOP))
                return true;
        }
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

    public static boolean attached(byte block) {
        int id = Byte.toUnsignedInt(block);
        return id >= Byte.toUnsignedInt(BlockId.VINE_EAST) && id <= Byte.toUnsignedInt(BlockId.COCOA_NORTH);
    }

    private static boolean attachmentSupported(World world, BlockPos p, byte plant, boolean keepUnknown) {
        int id = Byte.toUnsignedInt(plant);
        boolean vine = id <= Byte.toUnsignedInt(BlockId.VINE_NORTH);
        int side = (id - Byte.toUnsignedInt(vine ? BlockId.VINE_EAST : BlockId.COCOA_EAST)) % 4;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        long x = (long) p.x() + directions[side][0], z = (long) p.z() + directions[side][1];
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
            return false;
        var support = world.getBlock(new BlockPos((int) x, p.y(), (int) z));
        if (support.isEmpty()) return keepUnknown;
        byte block = (byte) support.getAsInt();
        if (vine ? BlockRegistry.DEFAULT.isCollidable(block) : block == BlockId.JUNGLE_LOG) return true;
        if (!vine || p.y() == Integer.MAX_VALUE) return false;
        var above = world.getBlock(new BlockPos(p.x(), p.y() + 1, p.z()));
        return above.isEmpty() ? keepUnknown : above.getAsInt() == id;
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
