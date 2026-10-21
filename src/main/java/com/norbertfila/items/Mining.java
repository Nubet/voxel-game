package com.norbertfila.items;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import java.util.Set;

/**
 * Minecraft mining rules on top of each block's bare-hand break time: the matching tool divides it by the tier
 * speed, and a block that needs a better pickaxe takes 10/3 as long and drops nothing.
 */
public final class Mining {
    private static final int NO_TOOL_NEEDED = -1;
    private static final double UNHARVESTABLE_PENALTY = 10 / 3d;
    private static final float SWORD_SPEED = 1.5f;
    private static final Set<String> LEVEL_ONE = Set.of("iron_ore", "lapis_ore", "iron_block", "lapis_block");
    private static final Set<String> LEVEL_TWO =
            Set.of("gold_ore", "diamond_ore", "gold_block", "diamond_block", "emerald_block");
    private static final Set<String> PICKAXE_OPTIONAL = Set.of("ice", "packed_ice", "blue_ice", "packed_mud");
    private static final Set<String> PICKAXE_REQUIRED = Set.of(
            "stone",
            "cobble",
            "mossy_cobble",
            "bricks",
            "mud_bricks",
            "calcite",
            "coal_ore",
            "coal_block",
            "granite",
            "polished_granite",
            "diorite",
            "polished_diorite",
            "andesite",
            "polished_andesite");
    private static final Tool.Type[] TOOL = new Tool.Type[256];
    private static final int[] LEVEL = new int[256];
    private static final boolean[] SWORD_FAST = new boolean[256];

    static {
        for (int id = 0; id < 256; id++) {
            LEVEL[id] = NO_TOOL_NEEDED;
            if (!BlockId.isValid((byte) id)) continue;
            String name = BlockRegistry.DEFAULT.definition((byte) id).name();
            if (LEVEL_TWO.contains(name)) require(id, 2);
            else if (LEVEL_ONE.contains(name)) require(id, 1);
            else if (PICKAXE_REQUIRED.contains(name)
                    || name.contains("stone_bricks")
                    || name.contains("sandstone")
                    || name.contains("tuff")
                    || name.startsWith("terracotta")
                    || name.startsWith("concrete_")) require(id, 0);
            else if (PICKAXE_OPTIONAL.contains(name)) TOOL[id] = Tool.Type.PICKAXE;
            else if (name.equals("log")
                    || name.endsWith("_log")
                    || name.endsWith("planks")
                    || name.equals("melon")
                    || name.equals("pumpkin")) TOOL[id] = Tool.Type.AXE;
            SWORD_FAST[id] = name.contains("leaves")
                    || name.equals("pine_needles")
                    || name.equals("melon")
                    || name.equals("pumpkin");
        }
    }

    private Mining() {}

    private static void require(int id, int level) {
        TOOL[id] = Tool.Type.PICKAXE;
        LEVEL[id] = level;
    }

    public static double seconds(byte block, ToolContext context) {
        double seconds = BlockRegistry.DEFAULT.definition(block).breakSeconds() / speed(block, context);
        return canHarvest(block, context) ? seconds : seconds * UNHARVESTABLE_PENALTY;
    }

    public static boolean canHarvest(byte block, ToolContext context) {
        int level = LEVEL[Byte.toUnsignedInt(block)];
        return level == NO_TOOL_NEEDED
                || context.tool()
                        .filter(tool ->
                                tool.type() == Tool.Type.PICKAXE && tool.tier().level() >= level)
                        .isPresent();
    }

    private static float speed(byte block, ToolContext context) {
        int id = Byte.toUnsignedInt(block);
        return context.tool()
                .map(tool -> tool.type() == TOOL[id]
                        ? tool.tier().speed()
                        : tool.type() == Tool.Type.SWORD && SWORD_FAST[id] ? SWORD_SPEED : 1)
                .orElse(1f);
    }
}
