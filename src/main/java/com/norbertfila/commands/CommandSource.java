package com.norbertfila.commands;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState;
import com.norbertfila.simulation.WorldTime;

public interface CommandSource {
    long seed();

    boolean setMode(PlayerController.Mode mode);

    int give(byte block, int count);

    WorldTime worldTime();

    void setTime(int minecraftTime);

    double tickRate();

    void setTickRate(double rate);

    default boolean teleport(PlayerState.Position position) {
        return false;
    }

    default boolean locate(BiomeType biome) {
        return false;
    }

    default boolean locateRift() {
        return false;
    }
}
