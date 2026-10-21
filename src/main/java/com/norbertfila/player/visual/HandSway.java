package com.norbertfila.player.visual;

import org.joml.Matrix4f;

/**
 * Minecraft's hand motion in first person: the hand trails fast turns, because the game rotates it by a tenth of the
 * gap between the view and a lagging copy of it, and walking bobs it along the stride.
 */
public final class HandSway {
    /** The lagging look closes half of the gap per 20 Hz tick. */
    private static final double CATCH_UP = 20 * Math.log(2);

    private static final float LAG_SCALE = .1f, MAX_BOB = .1f;
    private double laggedYaw, laggedPitch;
    private boolean started;

    public void update(double yaw, double pitch, double seconds) {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(seconds) || seconds < 0)
            throw new IllegalArgumentException("Invalid hand sway step");
        if (!started) {
            started = true;
            laggedYaw = yaw;
            laggedPitch = pitch;
            return;
        }
        double blend = -Math.expm1(-CATCH_UP * seconds);
        laggedYaw += (yaw - laggedYaw) * blend;
        laggedPitch += (pitch - laggedPitch) * blend;
    }

    /** View-space transform applied before the hand's own placement. */
    public Matrix4f transform(PlayerRenderState state, double alpha, double yaw, double pitch) {
        var a = state.animation().previous();
        var b = state.animation().current();
        double phase = a.phase() + (b.phase() - a.phase()) * alpha;
        double intensity = a.intensity() + (b.intensity() - a.intensity()) * alpha;
        double airborne = a.airborne() + (b.airborne() - a.airborne()) * alpha;
        // The animator advances its phase by 2.5 per block and Minecraft its walk distance by 0.6 per block.
        float stride = (float) -(phase / 2.5 * .6 * Math.PI);
        // Minecraft's bob grows with blocks per tick up to 0.1 and vanishes in the air.
        float bob = (float) (Math.min(MAX_BOB, intensity * PlayerAnimator.FULL_GAIT_SPEED / 20) * (1 - airborne));
        return new Matrix4f()
                .translation((float) Math.sin(stride) * bob * .5f, -Math.abs((float) Math.cos(stride) * bob), 0)
                .rotateZ((float) Math.toRadians(Math.sin(stride) * bob * 3))
                .rotateX((float) Math.toRadians(Math.abs(Math.cos(stride - .2) * bob) * 5))
                .rotateX((float) Math.toRadians((laggedPitch - pitch) * LAG_SCALE))
                .rotateY((float) Math.toRadians((yaw - laggedYaw) * LAG_SCALE));
    }
}
