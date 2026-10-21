package com.norbertfila.combat;

import com.norbertfila.items.Tool;

/** Minecraft Java melee stats: damage in health points and full swings per second. */
public record MeleeWeapon(float damage, float attackSpeed, boolean sweeps) {
    public static final MeleeWeapon HAND = new MeleeWeapon(1, 4, false);

    public MeleeWeapon {
        if (!Float.isFinite(damage) || damage < 0 || !Float.isFinite(attackSpeed) || attackSpeed <= 0)
            throw new IllegalArgumentException("Invalid melee weapon");
    }

    public static MeleeWeapon of(byte item) {
        return Tool.of(item).map(MeleeWeapon::of).orElse(HAND);
    }

    private static MeleeWeapon of(Tool tool) {
        int tier = tool.tier().ordinal();
        return switch (tool.type()) {
            case SWORD -> new MeleeWeapon(4 + tier, 1.6f, true);
            case PICKAXE -> new MeleeWeapon(2 + tier, 1.2f, false);
            case AXE ->
                switch (tool.tier()) {
                    case WOOD -> new MeleeWeapon(7, .8f, false);
                    case STONE -> new MeleeWeapon(9, .8f, false);
                    case IRON -> new MeleeWeapon(9, .9f, false);
                    case DIAMOND -> new MeleeWeapon(9, 1, false);
                };
        };
    }

    /** Seconds until a swing regains full strength. */
    public double cooldownSeconds() {
        return 1 / attackSpeed;
    }
}
