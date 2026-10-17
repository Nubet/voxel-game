package com.norbertfila.player.visual.model;

import org.joml.Matrix4f;

// Translation is in model pixels; rotations are right-handed radians, applied Z, Y, then X.
public record PartTransform(float x, float y, float z, float pitch, float yaw, float roll) {
    public static final PartTransform IDENTITY = new PartTransform(0, 0, 0, 0, 0, 0);

    public PartTransform {
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(z)
                || !Float.isFinite(pitch)
                || !Float.isFinite(yaw)
                || !Float.isFinite(roll)) throw new IllegalArgumentException("Part transform must be finite");
    }

    public Matrix4f apply(Matrix4f matrix) {
        return matrix.translate(x, y, z).rotateXYZ(pitch, yaw, roll);
    }
}
