package com.norbertfila.player.visual;

import com.norbertfila.player.PlayerController;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.PlayerModel.Part;
import com.norbertfila.player.visual.model.PlayerPose;
import java.util.EnumMap;

public final class PlayerAnimator {
    public static final int SWING_TICKS = 12;
    /** Horizontal speed in blocks per second at which the gait reaches full intensity. */
    public static final double FULL_GAIT_SPEED = 5.6;

    public record Swing(long sequence, long startTick) {
        public static final Swing NONE = new Swing(0, 0);

        public Swing {
            if (sequence < 0 || startTick < 0) throw new IllegalArgumentException("Invalid swing event");
        }
    }

    public record Frame(
            double phase,
            double intensity,
            double bodyYaw,
            double time,
            double airborne,
            double rise,
            double impact,
            double lean) {}

    public record Animation(Frame previous, Frame current, Swing swing) {}

    private Frame last;
    private Swing swing = Swing.NONE;
    private boolean grounded;
    private double velocityY;

    public void swing(long tick, boolean restart) {
        if (restart || swing.sequence() == 0 || tick - swing.startTick() >= SWING_TICKS)
            swing = new Swing(swing.sequence() + 1, tick);
    }

    Animation tick(PlayerController player, double yaw, boolean reset, boolean frozen, long tick) {
        var from = player.renderPosition(0);
        var to = player.state().position();
        double distance = Math.hypot(to.x() - from.x(), to.z() - from.z());
        boolean flying = player.flying() || player.mode() == PlayerController.Mode.SPECTATOR;
        double target = !flying ? Math.min(1, distance * 60 / FULL_GAIT_SPEED) : 0;
        double intensity = reset ? 0 : approach(last.intensity(), target, .22);
        double phase = reset ? 0 : last.phase() + (flying ? 0 : distance * 2.5);
        double airborne = reset ? 0 : approach(last.airborne(), !player.state().grounded() && !flying ? 1 : 0, .25);
        double rise =
                reset ? 0 : approach(last.rise(), Math.clamp(player.state().velocityY() / 9.6, -1, 1), .2);
        double landing =
                !reset && !grounded && player.state().grounded() && !flying ? Math.clamp(-velocityY / 12, 0, 1) : 0;
        double impact = reset ? 0 : Math.max(landing, last.impact() * .8);
        double lean = reset ? 0 : approach(last.lean(), player.sprinting() && !flying ? target : 0, .15);
        double body = reset ? yaw : last.bodyYaw();
        if (!reset) {
            double turn = angle(yaw - body);
            body += Math.clamp(turn, -6, 6);
            body = yaw - Math.clamp(angle(yaw - body), -60, 60);
        }
        double time = frozen && last != null ? last.time() : tick;
        var current = new Frame(phase, intensity, body, time, airborne, rise, impact, lean);
        var previous = reset ? current : last;
        last = current;
        grounded = player.state().grounded();
        velocityY = player.state().velocityY();
        return new Animation(previous, current, swing);
    }

    public static PlayerPose pose(PlayerRenderState state, double alpha) {
        var sample = state.interpolate(alpha);
        return pose(state, alpha, sample.yaw(), sample.pitch());
    }

    public static PlayerPose pose(PlayerRenderState state, double alpha, double lookYaw, double lookPitch) {
        var sample = state.interpolate(alpha);
        if (!Double.isFinite(lookYaw) || !Double.isFinite(lookPitch))
            throw new IllegalArgumentException("Invalid look angles");
        var a = state.animation().previous();
        var b = state.animation().current();
        double phase = lerp(a.phase(), b.phase(), alpha);
        double intensity = lerp(a.intensity(), b.intensity(), alpha);
        double time = lerp(a.time(), b.time(), alpha);
        double body = a.bodyYaw() + angle(b.bodyYaw() - a.bodyYaw()) * alpha;
        float rootYaw = radians(-angle(body - sample.yaw()));
        float headYaw = radians(-Math.clamp(angle(lookYaw - body), -60, 60));
        double air = lerp(a.airborne(), b.airborne(), alpha);
        double rise = lerp(a.rise(), b.rise(), alpha);
        double impact = lerp(a.impact(), b.impact(), alpha);
        float bend = state.crouching()
                ? -.5f
                : (float) (-Math.toRadians(10) * lerp(a.lean(), b.lean(), alpha) - impact * .12);
        float sway = (float) (Math.sin(phase) * intensity * .025 * (1 - air));
        boolean sprinting = state.sprinting();
        float leg = (float) (Math.sin(phase * (sprinting ? 1.35 : 1)) * intensity * (sprinting ? .68 : .75));
        float arm = (float) (Math.sin(phase * (sprinting ? .55 : 1) - (sprinting ? .45 : .3))
                * intensity
                * (sprinting ? 1.05 : .5));
        float rightArm = -arm, leftArm = arm, rightLeg = leg, leftLeg = -leg;
        float rest = (float) (Math.sin(time / 60 * 1.4) * .08 * (1 - intensity));
        rightArm += rest;
        leftArm -= rest;
        boolean flying = state.flying() || state.mode() == PlayerController.Mode.SPECTATOR;
        if (flying) {
            rightArm = leftArm = -.8f;
            rightLeg = .15f;
            leftLeg = -.15f;
        } else {
            float balance = (float) (-.35 * Math.max(rise, 0) + .25 * Math.max(-rise, 0));
            float tuck = (float) (-.4 * Math.max(rise, 0) + .12 * Math.max(-rise, 0));
            rightArm = (float) (rightArm * (1 - air * .15) + balance * air);
            leftArm = (float) (leftArm * (1 - air * .15) + balance * air);
            rightLeg = (float) (rightLeg * (1 - air * .1) + tuck * air - impact * .25);
            leftLeg = (float) (leftLeg * (1 - air * .1) - tuck * air - impact * .25);
        }
        double age = time - state.animation().swing().startTick();
        if (state.animation().swing().sequence() > 0 && age >= 0 && age < SWING_TICKS)
            rightArm += (float) (Math.sin(Math.PI * age / SWING_TICKS) * 1.8);
        float idle = state.frozen() ? 0 : (float) (Math.sin(time / 60 * 2) * .035);
        var parts = new EnumMap<Part, PartTransform>(Part.class);
        parts.put(
                Part.BODY,
                new PartTransform(
                        0,
                        (state.crouching() ? -2 : 0) - (float) impact * 1.5f,
                        state.crouching() ? 2 : 0,
                        bend,
                        rootYaw,
                        sway));
        parts.put(Part.HEAD, new PartTransform(0, 0, 0, radians(Math.clamp(lookPitch, -90, 90)) - bend, headYaw, 0));
        parts.put(Part.RIGHT_ARM, new PartTransform(0, 0, 0, rightArm - bend, 0, .04f + idle));
        parts.put(Part.LEFT_ARM, new PartTransform(0, 0, 0, leftArm - bend, 0, -.04f - idle));
        parts.put(
                Part.RIGHT_LEG,
                new PartTransform(
                        0,
                        (state.crouching() ? -1 : 0) - (float) impact * 1.5f,
                        state.crouching() ? 2 : 0,
                        rightLeg - bend * .4f,
                        rootYaw,
                        0));
        parts.put(
                Part.LEFT_LEG,
                new PartTransform(
                        0,
                        (state.crouching() ? -1 : 0) - (float) impact * 1.5f,
                        state.crouching() ? 2 : 0,
                        leftLeg - bend * .4f,
                        rootYaw,
                        0));
        return new PlayerPose(parts);
    }

    private static double lerp(double a, double b, double alpha) {
        return a + (b - a) * alpha;
    }

    private static double approach(double value, double target, double rate) {
        double result = lerp(value, target, rate);
        return Math.abs(result - target) < .0001 ? target : result;
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static double angle(double degrees) {
        return ((degrees % 360 + 540) % 360) - 180;
    }
}
