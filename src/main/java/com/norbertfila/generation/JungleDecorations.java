package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;

import com.norbertfila.generation.biome.TreeType;
import com.norbertfila.world.*;
import java.util.List;

final class JungleDecorations {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static void generate(List<TreeGenerator.Tree> trees, GroundSampler terrain, int ox, int oy, int oz, byte[] blocks) {
        for (var tree : trees) {
            var plan = tree.plan();
            if (plan == null) continue;
            long seed = tree.shapeSeed();
            plan.wood((x, y, z, block) -> {
                if (Math.floorMod(hash(seed, x * 31L + y, z), 17) != 0) return;
                int side = JungleTrees.bounded(hash(seed, x, z + (long) y), 3, 4);
                int nx = x + SIDES[side][0], nz = z + SIDES[side][1];
                byte vine = (byte) (Byte.toUnsignedInt(BlockId.VINE_EAST) + (side ^ 1));
                JungleVegetation.place(nx, y, nz, vine, ox, oy, oz, blocks);
            });
            if (tree.type() != TreeType.MAHOGANY && JungleTrees.bounded(seed, 29, 3) == 0) {
                int side = JungleTrees.bounded(seed, 30, 4);
                int x = tree.x() + SIDES[side][0], z = tree.z() + SIDES[side][1];
                if (tree.type() != TreeType.GIANT_JUNGLE || side == 1 || side == 3)
                    JungleVegetation.place(
                            x,
                            tree.baseY() + 2,
                            z,
                            (byte) (Byte.toUnsignedInt(BlockId.COCOA_EAST) + (side ^ 1)),
                            ox,
                            oy,
                            oz,
                            blocks);
            }
            for (int i = 0; i < plan.crowns().size(); i++) {
                var c = plan.crowns().get(i);
                for (int side = 0; side < 4; side++) {
                    long roll = hash(seed, i, side);
                    if (Math.floorMod(roll, tree.type() == TreeType.MAHOGANY ? 4 : 3) == 0) continue;
                    int x = c.x() + SIDES[side][0] * c.radius(), z = c.z() + SIDES[side][1] * c.radius();
                    int floor = terrain.surfaceHeight(x, z) + (Math.floorMod(roll, 7) == 0 ? 1 : 3);
                    int length = 2 + JungleTrees.bounded(roll, 4, tree.type() == TreeType.GIANT_JUNGLE ? 20 : 5);
                    byte vine = (byte) (Byte.toUnsignedInt(BlockId.VINE_EAST) + (side ^ 1));
                    for (int y = c.y(); y >= Math.max(floor, c.y() - length); y--)
                        JungleVegetation.place(x, y, z, vine, ox, oy, oz, blocks);
                }
            }
        }
    }

    static void ground(
            long seed, int ox, int oy, int oz, byte[] blocks, TerrainColumn columns, JungleVegetation jungle) {
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                int index = z * 16 + x;
                if (columns.biome(index) != com.norbertfila.generation.biome.BiomeType.JUNGLE
                        || !columns.surfaceSupported(index)
                        || columns.surfaceHeight(index) <= columns.waterLevel(index)
                        || !PlantRules.supports(columns.surfaceMaterial(index))) continue;
                int wx = ox + x, wz = oz + z, y = columns.surfaceHeight(index) + 1;
                long roll = hash(seed ^ 0xA4093822299F31D0L, wx, wz);
                double patch = jungle.understory(wx, wz);
                long cellX = Math.floorDiv(wx, 32), cellZ = Math.floorDiv(wz, 32);
                long fruit = hash(seed ^ 0x243F6A8885A308D3L, cellX, cellZ);
                long dx = wx - (cellX * 32 + 2 + JungleTrees.bounded(fruit, 1, 28));
                long dz = wz - (cellZ * 32 + 2 + JungleTrees.bounded(fruit, 2, 28));
                int count = 1 + JungleTrees.bounded(fruit, 3, 3);
                if (Math.floorMod(fruit, 8) == 0
                        && ((dx == 0 && dz == 0)
                                || (count > 1 && dx == 1 && dz == 0)
                                || (count > 2 && dx == 0 && dz == 1)))
                    JungleVegetation.place(
                            wx,
                            y,
                            wz,
                            JungleTrees.bounded(fruit, 4, 3) == 0 ? BlockId.PUMPKIN : BlockId.MELON,
                            ox,
                            oy,
                            oz,
                            blocks);
                else if (Math.floorMod(roll, 1024) == 0) {
                    byte flower = switch (JungleTrees.bounded(roll, 5, 3)) {
                        case 0 -> BlockId.DANDELION_YELLOW;
                        case 1 -> BlockId.DANDELION_WHITE;
                        default -> BlockId.POPPY;
                    };
                    JungleVegetation.place(wx, y, wz, flower, ox, oy, oz, blocks);
                } else if (patch > .02 && Math.floorMod(roll, 27) == 0) {
                    boolean fern = Math.floorMod(roll, 5) != 0;
                    int localY = y - oy;
                    if (localY >= 0 && localY < 16 && blocks[(localY * 16 + z) * 16 + x] != BlockId.AIR) continue;
                    if (localY + 1 >= 0 && localY + 1 < 16 && blocks[((localY + 1) * 16 + z) * 16 + x] != BlockId.AIR)
                        continue;
                    JungleVegetation.place(
                            wx, y, wz, fern ? BlockId.LARGE_FERN : BlockId.TALL_GRASS, ox, oy, oz, blocks);
                    JungleVegetation.place(
                            wx, y + 1, wz, fern ? BlockId.LARGE_FERN_TOP : BlockId.TALL_GRASS_TOP, ox, oy, oz, blocks);
                }
            }
    }
}
