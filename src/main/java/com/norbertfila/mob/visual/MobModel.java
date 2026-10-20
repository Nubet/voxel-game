package com.norbertfila.mob.visual;

import com.norbertfila.player.visual.model.ModelCuboid;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.UvOrigin;
import java.util.List;
import java.util.Objects;

/** Cuboid model in pixels with feet at Y = 0 and forward along -Z; each cuboid is relative to its part's pivot. */
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
