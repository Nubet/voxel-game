package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.player.visual.FirstPersonHand;
import com.norbertfila.player.visual.PlayerAppearance;
import com.norbertfila.world.BlockId;
import java.util.Objects;

public final class FirstPersonRenderer {
    private final PlayerRenderer players;

    /** Without a player renderer there is no skin, so the empty hand is not drawn. */
    public FirstPersonRenderer(PlayerRenderer players) {
        this.players = players;
    }

    public void render(int width, int height, PlayerAppearance appearance, byte heldItem) {
        Objects.requireNonNull(appearance);
        // The hand is drawn over the world with its own depth so that it never clips into nearby blocks.
        glClear(GL_DEPTH_BUFFER_BIT);
        var projection = FirstPersonHand.projection(width, height);
        if (heldItem == BlockId.AIR && players != null)
            players.renderArm(projection, FirstPersonHand.rightArm(appearance.model(), 0, 0), appearance);
    }
}
