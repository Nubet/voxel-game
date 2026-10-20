package com.norbertfila.mob.visual;

import com.norbertfila.player.visual.model.ModelCuboid;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.UvOrigin;
import java.util.List;
import java.util.Objects;

/**
 * Static cuboid model in pixels: feet at Y = 0, forward is -Z. Each part's cuboid is relative to its pivot, so a
 * future animation only has to rotate pivots.
 */
public record MobModel(String texture, int textureWidth, int textureHeight, List<Part> parts) {
    public record Part(PartTransform pivot, ModelCuboid cuboid, UvOrigin uv) {
        public Part {
            Objects.requireNonNull(pivot);
            Objects.requireNonNull(cuboid);
            Objects.requireNonNull(uv);
        }
    }

    public MobModel {
        Objects.requireNonNull(texture);
        if (textureWidth < 1 || textureHeight < 1) throw new IllegalArgumentException("Invalid texture size");
        parts = List.copyOf(parts);
        if (parts.isEmpty()) throw new IllegalArgumentException("Model needs at least one part");
    }

    public String textureResource() {
        return "/assets/textures/entity/" + texture + ".png";
    }
}
