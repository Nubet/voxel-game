package com.norbertfila.mob.visual;

import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.MobRenderState;
import com.norbertfila.simulation.FixedSimulation;

/**
 * Poses a mob for one frame the way Minecraft's models do: limbs swing with the distance walked, zombies lunge when
 * they hit, skeletons raise both arms to draw, and creepers swell and flash while their fuse burns.
 */
public final class MobAnimator {
    // Minecraft swings limbs by cos(limbSwing * 0.6662) with limbSwing growing about four units per block walked.
    static final double SWING_PER_BLOCK = 2.66;
    static final float SWING_ANGLE = 1.4f;
    // Minecraft's swing strength is four times the speed in blocks per 20 TPS tick, capped at one.
    static final double STRENGTH_PER_SPEED = 4 / 20.0;
    private static final float QUARTER_TURN = (float) (Math.PI / 2);

    /** Extra rotation in radians per model part, in model order, plus whole-body scale and a white flash. */
    public record Pose(float[] pitch, float[] yaw, float scaleXZ, float scaleY, boolean flash) {}

    private MobAnimator() {}

    public static Pose pose(MobRenderState state, double alpha) {
        var model = MobModels.of(state.type());
        int parts = model.parts().size();
        float[] pitch = new float[parts], yaw = new float[parts];
        double walked = state.interpolatedWalkDistance(alpha);
        double speed = (state.walkDistance() - state.previousWalkDistance()) * FixedSimulation.TICKS_PER_SECOND;
        float strength = (float) Math.min(1, speed * STRENGTH_PER_SPEED);
        float swing = (float) Math.cos(walked * SWING_PER_BLOCK) * SWING_ANGLE * strength;
        float progress = state.actionProgress();
        for (int i = 0; i < parts; i++) {
            switch (model.parts().get(i).limb()) {
                case RIGHT_LEG, FRONT_RIGHT_LEG, BACK_LEFT_LEG -> pitch[i] = swing;
                case LEFT_LEG, FRONT_LEFT_LEG, BACK_RIGHT_LEG -> pitch[i] = -swing;
                case RIGHT_ARM, LEFT_ARM -> {
                    boolean right = model.parts().get(i).limb() == MobModel.Limb.RIGHT_ARM;
                    pitch[i] = (right ? -swing : swing) * .5f;
                    if (state.action() == MobAction.AIMING) {
                        // Arms level with the shoulders; the bow arm reaches across the body like Minecraft's pose.
                        pitch[i] = QUARTER_TURN + (right ? .1f : 0);
                        yaw[i] = right ? -.1f : .4f;
                    } else if (state.action() == MobAction.ATTACKING && progress < .25f)
                        pitch[i] -= (float) Math.sin(progress / .25f * Math.PI) * .6f;
                }
                default -> {}
            }
        }
        float scaleXZ = 1, scaleY = 1;
        boolean flash = false;
        if (state.action() == MobAction.CHARGING && progress > 0) {
            float wobble = 1 + (float) Math.sin(progress * 100) * progress * .01f;
            float swell = progress * progress * progress;
            scaleXZ = (1 + swell * .4f) * wobble;
            scaleY = (1 + swell * .1f) / wobble;
            flash = (int) (progress * 10) % 2 == 1;
        }
        return new Pose(pitch, yaw, scaleXZ, scaleY, flash);
    }
}
