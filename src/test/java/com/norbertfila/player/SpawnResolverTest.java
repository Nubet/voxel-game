package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.generation.HillsGenerator;
import org.junit.jupiter.api.Test;

class SpawnResolverTest {
    @Test
    void placesFeetOnTheFirstAirBlockAboveTerrain() {
        HillsGenerator terrain = new HillsGenerator();

        PlayerState.Position spawn = new SpawnResolver(terrain).resolve(0, 0);

        assertEquals(0.5, spawn.x());
        assertEquals(terrain.sample(0, 0).surfaceHeight() + 1.0, spawn.y());
        assertEquals(0.5, spawn.z());
    }
}
