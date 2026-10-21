package com.norbertfila.mob.visual;

import com.norbertfila.player.visual.model.ModelCuboid;
import com.norbertfila.player.visual.model.PartTransform;
import com.norbertfila.player.visual.model.UvOrigin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Cuboid model in pixels with feet at Y = 0 and forward along -Z; each cuboid is relative to its part's pivot. */
public record MobModel(
        String texture, int textureWidth, int textureHeight, List<Part> parts, Optional<HeldItem> heldItem) {
    /** What a part is, so that animation can move it without knowing the mob type. */
    public enum Limb {
        HEAD,
        BODY,
        RIGHT_ARM,
        LEFT_ARM,
        RIGHT_LEG,
        LEFT_LEG,
        FRONT_RIGHT_LEG,
        FRONT_LEFT_LEG,
        BACK_RIGHT_LEG,
        BACK_LEFT_LEG
    }

    public record Part(Limb limb, PartTransform pivot, ModelCuboid cuboid, UvOrigin uv) {
        public Part {
            Objects.requireNonNull(limb);
            Objects.requireNonNull(pivot);
            Objects.requireNonNull(cuboid);
            Objects.requireNonNull(uv);
        }
    }

    /**
     * A flat 16x16 item sprite held in a limb, such as a skeleton's bow. The textures are the idle look followed by
     * the frames shown while the item is used, in order.
     */
    public record HeldItem(Limb limb, List<String> textures) {
        public HeldItem {
            Objects.requireNonNull(limb);
            textures = List.copyOf(textures);
            if (textures.isEmpty()) throw new IllegalArgumentException("A held item needs a texture");
        }

        public String textureResource(int frame) {
            return "/assets/textures/entity/" + textures.get(frame) + ".png";
        }
    }

    public MobModel(String texture, int textureWidth, int textureHeight, List<Part> parts) {
        this(texture, textureWidth, textureHeight, parts, Optional.empty());
    }

    public MobModel {
        Objects.requireNonNull(texture);
        Objects.requireNonNull(heldItem);
        if (textureWidth < 1 || textureHeight < 1) throw new IllegalArgumentException("Invalid texture size");
        parts = List.copyOf(parts);
        if (parts.isEmpty()) throw new IllegalArgumentException("Model needs at least one part");
    }

    public String textureResource() {
        return "/assets/textures/entity/" + texture + ".png";
    }
}
