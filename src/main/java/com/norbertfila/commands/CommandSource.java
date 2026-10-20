package com.norbertfila.commands;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.mob.MobCensus;
import com.norbertfila.mob.MobType;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherType;
import java.util.Optional;

public interface CommandSource {
    long seed();

    boolean setMode(PlayerController.Mode mode);

    int give(byte block, int count);

    WorldTime worldTime();

    void setTime(int minecraftTime);

    double tickRate();

    void setTickRate(double rate);

    WeatherType weather();

    void setWeather(WeatherType type);

    default boolean teleport(PlayerState.Position position) {
        return false;
    }

    default boolean locate(BiomeType biome) {
        return false;
    }

    default boolean locateRift() {
        return false;
    }

    default Optional<PlayerState.Position> playerPosition() {
        return Optional.empty();
    }

    default boolean summon(MobType type, PlayerState.Position position) {
        return false;
    }

    default MobCensus mobs() {
        return MobCensus.EMPTY;
    }

    default void setMobSpawning(boolean enabled) {}

    default int removeMobs() {
        return 0;
    }
}
