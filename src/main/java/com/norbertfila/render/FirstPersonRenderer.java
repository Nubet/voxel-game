package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.player.visual.FirstPersonHand;
import com.norbertfila.player.visual.PlayerAppearance;
import com.norbertfila.world.BlockId;
import java.util.Objects;

public final class FirstPersonRenderer {
    private final PlayerRenderer players;
    private final ItemEntityRenderer items;

    /** Without a player renderer there is no skin, so the empty hand is not drawn. */
    public FirstPersonRenderer(PlayerRenderer players, ItemEntityRenderer items) {
        this.players = players;
        this.items = Objects.requireNonNull(items);
    }

    public void render(int width, int height, PlayerAppearance appearance, byte heldItem, float swing, int blockLight) {
        Objects.requireNonNull(appearance);
        // The hand is drawn over the world with its own depth so that it never clips into nearby blocks.
        glClear(GL_DEPTH_BUFFER_BIT);
        var projection = FirstPersonHand.projection(width, height);
        if (heldItem != BlockId.AIR) {
            boolean block = ItemModel.held(heldItem).shape() == ItemModel.Shape.CUBE;
            items.renderHeld(projection, FirstPersonHand.rightHandItem(block, swing, 0), heldItem, blockLight);
        } else if (players != null)
            players.renderArm(projection, FirstPersonHand.rightArm(appearance.model(), swing, 0), appearance);
    }
}
