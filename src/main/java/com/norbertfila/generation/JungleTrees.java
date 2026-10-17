package com.norbertfila.generation;

import static com.norbertfila.generation.GenerationHash.hash;

import com.norbertfila.generation.biome.TreeType;
import com.norbertfila.world.BlockId;
import java.util.ArrayList;

final class JungleTrees {
    static TreePlan plan(int x, int y, int z, long seed, TreeType type) {
        boolean giant = type == TreeType.GIANT_JUNGLE, mahogany = type == TreeType.MAHOGANY;
        int height = giant ? 18 + bounded(seed, 0, 15) : mahogany ? 12 + bounded(seed, 0, 11) : 7 + bounded(seed, 0, 6);
        int radius = giant ? 5 : mahogany ? 4 : 3;
        int top = y + height - 1;
        var branches = new ArrayList<TreePlan.Branch>();
        for (int dz = 0; dz < (giant ? 2 : 1); dz++)
            for (int dx = 0; dx < (giant ? 2 : 1); dx++)
                branches.add(new TreePlan.Branch(x + dx, y, z + dz, x + dx, top - 2, z + dz));
        var crowns = new ArrayList<TreePlan.Crown>();
        crowns.add(new TreePlan.Crown(x, top - 2, z, radius, 2, seed));
        int count = giant ? 4 : mahogany ? 3 : 0;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + bounded(seed, 1, 64) / 64d;
            int bx = x + (int) Math.round(Math.cos(angle) * (radius - 1));
            int bz = z + (int) Math.round(Math.sin(angle) * (radius - 1));
            int by = y + (giant ? height / 2 : height - 6) + bounded(seed, 2 + i, giant ? height / 3 : 3);
            branches.add(new TreePlan.Branch(x, by - 2, z, bx, by, bz));
            crowns.add(new TreePlan.Crown(bx, by + 1, bz, giant ? 3 : 4, 2, hash(seed, i, 9)));
        }
        return new TreePlan(
                branches,
                crowns,
                mahogany ? BlockId.MAHOGANY_LOG : BlockId.JUNGLE_LOG,
                mahogany ? BlockId.MAHOGANY_LEAVES : BlockId.JUNGLE_LEAVES);
    }

    static int bounded(long seed, int index, int bound) {
        return (int) Long.remainderUnsigned(hash(seed, index, 0), bound);
    }
}
