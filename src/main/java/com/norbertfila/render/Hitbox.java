package com.norbertfila.render;

import com.norbertfila.world.Aabb;
import java.util.Objects;

/** An entity's collision box, eye height and look direction in world coordinates; a zero look draws no line. */
public record Hitbox(Aabb box, double eyeY, double lookX, double lookY, double lookZ) {
    public Hitbox {
        Objects.requireNonNull(box);
        if (!Double.isFinite(eyeY) || !Double.isFinite(lookX) || !Double.isFinite(lookY) || !Double.isFinite(lookZ))
            throw new IllegalArgumentException("Hitbox values must be finite");
    }

    public boolean hasLook() {
        return lookX != 0 || lookY != 0 || lookZ != 0;
    }
}
