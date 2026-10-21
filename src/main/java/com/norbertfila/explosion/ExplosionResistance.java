package com.norbertfila.explosion;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;

/**
 * Minecraft blast resistance per block, grouped by material family through block names so that new variants of a
 * family need no entry here. Higher values stop more of an explosion; fluids and bedrock effectively stop all of it.
 */
public final class ExplosionResistance {
    static final float INDESTRUCTIBLE = 3_600_000;
    private static final float[] TABLE = new float[256];

    static {
        for (int id = 0; id < 256; id++)
            if (BlockRegistry.DEFAULT.isRegistered((byte) id)) TABLE[id] = classify((byte) id);
    }

    private ExplosionResistance() {}

    public static float of(byte block) {
        return TABLE[Byte.toUnsignedInt(block)];
    }

    private static float classify(byte block) {
        int id = Byte.toUnsignedInt(block);
        if (block == BlockId.BEDROCK) return INDESTRUCTIBLE;
        if (BlockId.isFluid(id)) return 100;
        var definition = BlockRegistry.DEFAULT.definition(block);
        if (block == BlockId.AIR || !definition.collidable()) return 0;
        String name = definition.name();
        if (name.contains("sandstone")) return .8f;
        if (name.contains("ore") || name.endsWith("_block")) return name.endsWith("_block") ? 6 : 3;
        if (name.contains("stone")
                || name.contains("cobble")
                || name.contains("brick")
                || name.contains("andesite")
                || name.contains("diorite")
                || name.contains("granite")
                || name.contains("tuff")) return 6;
        if (name.contains("terracotta")) return 4.2f;
        if (name.contains("concrete")) return 1.8f;
        if (name.contains("calcite")) return .75f;
        if (name.contains("log") || name.contains("planks")) return name.contains("planks") ? 3 : 2;
        if (name.contains("wool")) return .8f;
        if (name.contains("leaves") || name.contains("needles")) return .2f;
        if (name.contains("glass")) return .3f;
        if (name.contains("ice")) return name.contains("blue") || name.contains("packed") ? 2.8f : .5f;
        if (name.contains("snow")) return .1f;
        if (name.contains("cactus")) return .4f;
        if (name.contains("melon") || name.contains("pumpkin")) return 1;
        // Dirt, grass, sand, gravel, clay, mud and moss are all soft ground.
        return .5f;
    }
}
