package com.norbertfila.player.visual;

import org.joml.Matrix4f;

/** Minecraft's item model parents, which choose how an item sits in the hand. */
public enum HeldModel {
    BLOCK,
    GENERATED,
    HANDHELD;

    /** The model's third-person right hand display transform, in Minecraft's translate, rotate, scale order. */
    Matrix4f applyThirdPerson(Matrix4f matrix) {
        return switch (this) {
            case BLOCK ->
                matrix.translate(0, 2.5f / 16, 0)
                        .rotateXYZ((float) Math.toRadians(75), (float) Math.toRadians(45), 0)
                        .scale(.375f);
            case GENERATED -> matrix.translate(0, 3 / 16f, 1 / 16f).scale(.55f);
            case HANDHELD ->
                matrix.translate(0, 4 / 16f, .5f / 16)
                        .rotateXYZ(0, (float) Math.toRadians(-90), (float) Math.toRadians(55))
                        .scale(.85f);
        };
    }
}
