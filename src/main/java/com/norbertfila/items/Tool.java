package com.norbertfila.items;

import java.util.Locale;
import java.util.Optional;

/** Hand tools stored in the item ids above every block id. */
public enum Tool {
    WOODEN_PICKAXE(Type.PICKAXE, Tier.WOOD),
    WOODEN_AXE(Type.AXE, Tier.WOOD),
    WOODEN_SWORD(Type.SWORD, Tier.WOOD),
    STONE_PICKAXE(Type.PICKAXE, Tier.STONE),
    STONE_AXE(Type.AXE, Tier.STONE),
    STONE_SWORD(Type.SWORD, Tier.STONE),
    IRON_PICKAXE(Type.PICKAXE, Tier.IRON),
    IRON_AXE(Type.AXE, Tier.IRON),
    IRON_SWORD(Type.SWORD, Tier.IRON),
    DIAMOND_PICKAXE(Type.PICKAXE, Tier.DIAMOND),
    DIAMOND_AXE(Type.AXE, Tier.DIAMOND),
    DIAMOND_SWORD(Type.SWORD, Tier.DIAMOND);

    public static final int FIRST_ID = 200;
    private static final Tool[] VALUES = values();
    private final Type type;
    private final Tier tier;

    Tool(Type type, Tier tier) {
        this.type = type;
        this.tier = tier;
    }

    public Type type() {
        return type;
    }

    public Tier tier() {
        return tier;
    }

    public byte id() {
        return (byte) (FIRST_ID + ordinal());
    }

    public String itemName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Tool> of(byte id) {
        int index = Byte.toUnsignedInt(id) - FIRST_ID;
        return index >= 0 && index < VALUES.length ? Optional.of(VALUES[index]) : Optional.empty();
    }

    public enum Type {
        PICKAXE,
        AXE,
        SWORD
    }

    /** Minecraft tool materials: harvest level and mining speed multiplier. */
    public enum Tier {
        WOOD(0, 2),
        STONE(1, 4),
        IRON(2, 6),
        DIAMOND(3, 8);

        private final int level;
        private final float speed;

        Tier(int level, float speed) {
            this.level = level;
            this.speed = speed;
        }

        public int level() {
            return level;
        }

        public float speed() {
            return speed;
        }
    }
}
