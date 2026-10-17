package com.norbertfila.player.visual.model;

import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;

public final class PlayerModel {
    public static final float PIXEL_SCALE = 1f / 16;

    public enum Part {
        BODY,
        HEAD,
        RIGHT_ARM,
        LEFT_ARM,
        RIGHT_LEG,
        LEFT_LEG
    }

    public record PartDefinition(Part part, Part parent, PartTransform pivot, ModelCuboid cuboid, UvOrigin uvOrigin) {}

    public record OuterLayer(Part part, UvOrigin uvOrigin, float inflation) {}

    public static final List<OuterLayer> OUTER_LAYERS = List.of(
            new OuterLayer(Part.BODY, new UvOrigin(16, 32), .25f),
            new OuterLayer(Part.HEAD, new UvOrigin(32, 0), .5f),
            new OuterLayer(Part.RIGHT_ARM, new UvOrigin(40, 32), .25f),
            new OuterLayer(Part.LEFT_ARM, new UvOrigin(48, 48), .25f),
            new OuterLayer(Part.RIGHT_LEG, new UvOrigin(0, 32), .25f),
            new OuterLayer(Part.LEFT_LEG, new UvOrigin(0, 48), .25f));

    // Feet are at Y=0, up is +Y, forward is -Z, and the character's right is +X.
    // Head and shoulders inherit the torso; hips inherit only the external model transform.
    public static final List<PartDefinition> PARTS = List.of(
            part(Part.BODY, null, 0, 12, new ModelCuboid(-4, 0, -2, 8, 12, 4), 16, 16),
            part(Part.HEAD, Part.BODY, 0, 12, new ModelCuboid(-4, 0, -4, 8, 8, 8), 0, 0),
            part(Part.RIGHT_ARM, Part.BODY, 5, 10, new ModelCuboid(-1, -10, -2, 4, 12, 4), 40, 16),
            part(Part.LEFT_ARM, Part.BODY, -5, 10, new ModelCuboid(-3, -10, -2, 4, 12, 4), 32, 48),
            part(Part.RIGHT_LEG, null, 2, 12, new ModelCuboid(-2, -12, -2, 4, 12, 4), 0, 16),
            part(Part.LEFT_LEG, null, -2, 12, new ModelCuboid(-2, -12, -2, 4, 12, 4), 16, 48));

    private PlayerModel() {}

    private static PartDefinition part(Part part, Part parent, float x, float y, ModelCuboid cuboid, int u, int v) {
        return new PartDefinition(part, parent, new PartTransform(x, y, 0, 0, 0, 0), cuboid, new UvOrigin(u, v));
    }

    public static PartDefinition definition(Part part) {
        return PARTS.get(Objects.requireNonNull(part).ordinal());
    }

    public static Matrix4f partMatrix(Part part, PlayerPose pose, Matrix4f destination) {
        Objects.requireNonNull(pose);
        destination.scaling(PIXEL_SCALE);
        return applyHierarchy(part, pose, destination);
    }

    private static Matrix4f applyHierarchy(Part part, PlayerPose pose, Matrix4f matrix) {
        var definition = definition(part);
        if (definition.parent() != null) applyHierarchy(definition.parent(), pose, matrix);
        definition.pivot().apply(matrix);
        return pose.transform(part).apply(matrix);
    }
}
