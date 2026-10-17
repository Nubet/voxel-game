package com.norbertfila.player.visual;

import java.util.Objects;

public record PlayerAppearance(Model model) {
    public static final PlayerAppearance DEFAULT = new PlayerAppearance(Model.CLASSIC);

    public enum Model {
        CLASSIC
    }

    public PlayerAppearance {
        Objects.requireNonNull(model);
    }
}
