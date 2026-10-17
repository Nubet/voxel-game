package com.norbertfila.player.visual;

import com.norbertfila.player.visual.model.PlayerModel.Part;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record PlayerAppearance(Model model, Set<Part> outerLayers) {
    public static final PlayerAppearance DEFAULT = new PlayerAppearance(Model.CLASSIC);

    public enum Model {
        CLASSIC
    }

    public PlayerAppearance {
        Objects.requireNonNull(model);
        outerLayers = Set.copyOf(outerLayers);
    }

    public PlayerAppearance(Model model) {
        this(model, EnumSet.allOf(Part.class));
    }

    public PlayerAppearance withOuterLayer(Part part, boolean visible) {
        Objects.requireNonNull(part);
        var layers = EnumSet.noneOf(Part.class);
        layers.addAll(outerLayers);
        if (visible) layers.add(part);
        else layers.remove(part);
        return new PlayerAppearance(model, layers);
    }
}
