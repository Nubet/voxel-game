package com.norbertfila.player.visual;

import com.norbertfila.player.visual.model.ModelVariant;
import com.norbertfila.player.visual.model.PlayerModel;
import com.norbertfila.player.visual.model.PlayerPose;
import java.util.Objects;
import org.joml.Matrix4f;

/** Minecraft's ItemInHandLayer placement of a held item on the right arm. */
public final class ThirdPersonHand {
    private ThirdPersonHand() {}

    /** Maps an item model over unit space to the space of {@code modelToWorld}. */
    public static Matrix4f rightHandItem(
            Matrix4f modelToWorld, ModelVariant variant, PlayerPose pose, HeldModel model) {
        Objects.requireNonNull(model);
        var arm = PlayerModel.partMatrix(variant, PlayerModel.Part.RIGHT_ARM, pose, new Matrix4f())
                .scale(16);
        var matrix = new Matrix4f(modelToWorld)
                .mul(arm)
                // Minecraft's model space points X left and Y down, a half turn about Z from ours.
                .rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(-90))
                .rotateY((float) Math.PI)
                .translate(1 / 16f, .125f, -.625f);
        return model.applyThirdPerson(matrix).translate(-.5f, -.5f, -.5f);
    }
}
