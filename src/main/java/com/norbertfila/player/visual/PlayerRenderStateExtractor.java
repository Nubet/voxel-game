package com.norbertfila.player.visual;

import com.norbertfila.player.PlayerController;
import com.norbertfila.player.WaterContact;

/** Owned by the simulation thread; published records contain no controller or graphics resources. */
public final class PlayerRenderStateExtractor {
    private PlayerRenderState.Sample last;
    private long resetRevision;
    private long tick;
    private final PlayerAnimator animator = new PlayerAnimator();

    public void swing(long tick, boolean restart) {
        animator.swing(tick, restart);
    }

    public PlayerRenderState extract(
            PlayerController player, double yaw, double pitch, WaterContact.Result water, boolean frozen) {
        return extract(player, yaw, pitch, water, frozen, PlayerAppearance.DEFAULT);
    }

    public PlayerRenderState extract(
            PlayerController player,
            double yaw,
            double pitch,
            WaterContact.Result water,
            boolean frozen,
            PlayerAppearance appearance) {
        return extract(player, yaw, pitch, water, frozen, appearance, ++tick);
    }

    public PlayerRenderState extract(
            PlayerController player,
            double yaw,
            double pitch,
            WaterContact.Result water,
            boolean frozen,
            PlayerAppearance appearance,
            long tick) {
        if (tick < 0) throw new IllegalArgumentException("Invalid animation tick");
        this.tick = tick;
        var state = player.state();
        var current = new PlayerRenderState.Sample(state.position(), state.eyeHeight(), yaw, pitch);
        boolean reset = last == null || resetRevision != player.interpolationResetRevision() || frozen;
        var previous = reset
                ? current
                : new PlayerRenderState.Sample(
                        player.renderPosition(0),
                        player.renderEyePosition(0).y()
                                - player.renderPosition(0).y(),
                        last.yaw(),
                        last.pitch());
        last = current;
        resetRevision = player.interpolationResetRevision();
        return new PlayerRenderState(
                appearance,
                previous,
                current,
                state.velocityX(),
                state.velocityY(),
                state.velocityZ(),
                state.grounded(),
                state.crouching(),
                player.sprinting(),
                player.flying(),
                player.mode(),
                water.submergedFraction(),
                water.eyesSubmerged(),
                water.available(),
                frozen,
                animator.tick(player, yaw, reset, frozen, tick));
    }
}
