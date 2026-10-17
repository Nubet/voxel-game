package com.norbertfila.player.visual;

import com.norbertfila.player.PlayerController;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.PlayerModel.Part;
import com.norbertfila.player.visual.model.PlayerPose;
import java.util.EnumMap;

public final class PlayerAnimator {
    public static final int SWING_TICKS = 12;

    public record Swing(long sequence, long startTick) {
        public static final Swing NONE = new Swing(0, 0);

        public Swing {
            if (sequence < 0 || startTick < 0) throw new IllegalArgumentException("Invalid swing event");
        }
    }

    public record Frame(double phase, double intensity, double bodyYaw, double time) {}

    public record Animation(Frame previous, Frame current, Swing swing) {}

    private Frame last;
    private Swing swing = Swing.NONE;

    public void swing(long tick, boolean restart) {
        if (restart || swing.sequence() == 0 || tick - swing.startTick() >= SWING_TICKS)
            swing = new Swing(swing.sequence() + 1, tick);
    }

    Animation tick(PlayerController player, double yaw, boolean reset, boolean frozen, long tick) {
        var from = player.renderPosition(0);
        var to = player.state().position();
        double distance = Math.hypot(to.x() - from.x(), to.z() - from.z());
        boolean flying = player.flying() || player.mode() == PlayerController.Mode.SPECTATOR;
        double intensity = !reset && player.state().grounded() && !flying ? Math.min(1, distance * 60 / 5.6) : 0;
        double phase = reset || intensity == 0 ? 0 : last.phase() + distance * 2.5;
        double body = reset ? yaw : last.bodyYaw();
        if (!reset) {
            double turn = angle(yaw - body);
            body += Math.clamp(turn, -6, 6);
            body = yaw - Math.clamp(angle(yaw - body), -60, 60);
        }
        double time = frozen && last != null ? last.time() : tick;
        var current = new Frame(phase, intensity, body, time);
        var previous = reset ? current : intensity == 0 ? new Frame(0, 0, last.bodyYaw(), last.time()) : last;
        last = current;
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
        float bend = state.crouching() ? -.5f : 0;
        float leg = (float) (Math.sin(phase) * intensity * (state.sprinting() ? 1.1 : .75));
        float rightArm = -leg, leftArm = leg, rightLeg = leg, leftLeg = -leg;
        boolean flying = state.flying() || state.mode() == PlayerController.Mode.SPECTATOR;
        if (flying) {
            rightArm = leftArm = -.8f;
            rightLeg = .15f;
            leftLeg = -.15f;
        } else if (!state.grounded()) {
            boolean rising = state.velocityY() > 0;
            rightArm = leftArm = rising ? -.45f : .25f;
            rightLeg = rising ? -.35f : .12f;
            leftLeg = rising ? .35f : -.12f;
        }
        double age = time - state.animation().swing().startTick();
        if (state.animation().swing().sequence() > 0 && age >= 0 && age < SWING_TICKS)
            rightArm += (float) (Math.sin(Math.PI * age / SWING_TICKS) * 1.8);
        float idle = state.frozen() ? 0 : (float) (Math.sin(time / 60 * 2) * .035);
        var parts = new EnumMap<Part, PartTransform>(Part.class);
        parts.put(
                Part.BODY,
                new PartTransform(0, state.crouching() ? -2 : 0, state.crouching() ? 2 : 0, bend, rootYaw, 0));
        parts.put(Part.HEAD, new PartTransform(0, 0, 0, radians(Math.clamp(lookPitch, -90, 90)) - bend, headYaw, 0));
        parts.put(Part.RIGHT_ARM, new PartTransform(0, 0, 0, rightArm - bend, 0, .04f + idle));
        parts.put(Part.LEFT_ARM, new PartTransform(0, 0, 0, leftArm - bend, 0, -.04f - idle));
        parts.put(
                Part.RIGHT_LEG,
                new PartTransform(
                        0, state.crouching() ? -1 : 0, state.crouching() ? 2 : 0, rightLeg - bend * .4f, rootYaw, 0));
        parts.put(
                Part.LEFT_LEG,
                new PartTransform(
                        0, state.crouching() ? -1 : 0, state.crouching() ? 2 : 0, leftLeg - bend * .4f, rootYaw, 0));
        return new PlayerPose(parts);
    }

    private static double lerp(double a, double b, double alpha) {
        return a + (b - a) * alpha;
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static double angle(double degrees) {
        return ((degrees % 360 + 540) % 360) - 180;
    }
}
