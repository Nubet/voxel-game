package com.norbertfila.player.visual;

import com.norbertfila.player.visual.model.ModelVariant;
import java.util.Objects;
import org.joml.Matrix4f;

/** Minecraft's first-person hand transforms from ItemInHandRenderer, in view space. */
public final class FirstPersonHand {
    /** Minecraft draws the hand with a fixed field of view, so zoom and FOV settings do not resize it. */
    public static final float FIELD_OF_VIEW = 70;

    public static final float NEAR = .05f, FAR = 16;
    private static final float PI = (float) Math.PI;

    private FirstPersonHand() {}

    public static Matrix4f projection(int width, int height) {
        return new Matrix4f()
                .perspective((float) Math.toRadians(FIELD_OF_VIEW), (float) width / Math.max(1, height), NEAR, FAR);
    }

    /**
     * Maps the right arm's cuboid, in this model's pixel units around its shoulder pivot, to view space. Swing and
     * equip are in [0, 1]; equip 1 hides the arm below the screen.
     */
    public static Matrix4f rightArm(ModelVariant variant, float swing, float equip) {
        Objects.requireNonNull(variant);
        float root = (float) Math.sqrt(swing);
        return new Matrix4f()
                .translation(
                        -.3f * sin(root * PI) + .64f,
                        .4f * sin(root * 2 * PI) - .6f - equip * .6f,
                        -.4f * sin(swing * PI) - .72f)
                .rotateY(radians(45))
                .rotateY(radians(sin(root * PI) * 70))
                .rotateZ(radians(sin(swing * swing * PI) * -20))
                .translate(-1, 3.6f, 3.5f)
                .rotateZ(radians(120))
                .rotateX(radians(200))
                .rotateY(radians(-135))
                .translate(5.6f, 0, 0)
                .translate(-5 / 16f, (variant == ModelVariant.SLIM ? 2.5f : 2) / 16f, 0)
                .scale(1 / 16f)
                // Minecraft's model space points X left and Y down, a half turn about Z from ours.
                .rotateZ(PI);
    }

    private static float sin(float radians) {
        return (float) Math.sin(radians);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
