package com.norbertfila.ui.crosshair;

import com.norbertfila.interaction.VoxelRaycaster;
import java.util.Objects;
import java.util.Optional;

public final class CrosshairPolicy {
    private CrosshairPolicy() {}

    public static CrosshairStyle select(CrosshairSettings settings, Optional<VoxelRaycaster.Hit> target) {
        Objects.requireNonNull(settings);
        Objects.requireNonNull(target);
        if (!settings.dynamic()) return CrosshairStyle.DEFAULT;
        if (target.isPresent()) return CrosshairStyle.BLOCK;
        return settings.hideWithoutTarget() ? CrosshairStyle.HIDDEN : CrosshairStyle.DEFAULT;
    }
}
