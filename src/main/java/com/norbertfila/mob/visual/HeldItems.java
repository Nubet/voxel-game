package com.norbertfila.mob.visual;

import com.norbertfila.mob.MobAction;

/**
 * Places a held 16x16 item sprite in a hand the way Minecraft holds a bow: the sprite's diagonal limb stands upright
 * across the arm's end, and the arrow corner of the icon points along the arm, so a raised arm aims the bow forward.
 */
public final class HeldItems {
    public static final int FLOATS_PER_VERTEX = 5;
    public static final int VERTICES = 6;
    /** Model pixels per sprite texel; Minecraft draws held items a little smaller than a block face. */
    static final float SCALE = .75f;
    /** The hand sits at the end of a twelve pixel arm hanging from its pivot. */
    static final float HAND_Y = -10;
    /** The grip texel of the bow sprite; the sprite is shifted so the grip lands in the hand. */
    static final float GRIP_U = 5, GRIP_V = 5;

    private HeldItems() {}

    /**
     * Two triangles in limb space, XYZ in model pixels plus UV. Along the arm (local -Y) runs the sprite's
     * top-left diagonal, and local -Z, which is up once the arm is raised, runs along the bow string.
     */
    public static float[] quad() {
        float[][] corners = {corner(0, 16), corner(16, 16), corner(16, 0), corner(0, 0)};
        var out = new float[VERTICES * FLOATS_PER_VERTEX];
        int at = 0;
        for (int index : new int[] {0, 1, 2, 0, 2, 3}) {
            System.arraycopy(corners[index], 0, out, at, FLOATS_PER_VERTEX);
            at += FLOATS_PER_VERTEX;
        }
        return out;
    }

    private static float[] corner(float u, float v) {
        float invSqrt2 = (float) (1 / Math.sqrt(2));
        float du = u - GRIP_U, dv = v - GRIP_V;
        float along = (-du - dv) * invSqrt2 * SCALE;
        float upward = (du - dv) * invSqrt2 * SCALE;
        return new float[] {0, HAND_Y - along, -upward, u / 16, v / 16};
    }

    /** Minecraft swaps the bow icon as the pull grows: below 65%, below 90%, then fully drawn. */
    public static int frame(MobAction action, float progress) {
        if (action != MobAction.AIMING) return 0;
        if (progress < .65f) return 1;
        return progress < .9f ? 2 : 3;
    }
}
