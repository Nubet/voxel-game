package com.norbertfila.player.visual.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record PlayerPose(Map<PlayerModel.Part, PartTransform> parts) {
    public static final PlayerPose NEUTRAL = new PlayerPose(Map.of());

    public PlayerPose {
        parts = Map.copyOf(parts);
    }

    public PartTransform transform(PlayerModel.Part part) {
        return parts.getOrDefault(Objects.requireNonNull(part), PartTransform.IDENTITY);
    }

    public PlayerPose with(PlayerModel.Part part, PartTransform transform) {
        var copy = new EnumMap<PlayerModel.Part, PartTransform>(PlayerModel.Part.class);
        copy.putAll(parts);
        copy.put(Objects.requireNonNull(part), Objects.requireNonNull(transform));
        return new PlayerPose(copy);
    }
}
