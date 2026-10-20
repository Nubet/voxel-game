package com.norbertfila.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.*;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MobCommandsTest {
    private final GameCommands commands = new GameCommands();
    private final Source source = new Source();

    @Test
    void summonUsesThePlayerPositionOrExplicitCoordinates() {
        assertEquals(
                "Summoned zombie", commands.execute("/summon zombie", source).text());
        assertEquals(new PlayerState.Position(1, 2, 3), source.summoned.getLast());
        assertFalse(commands.execute("/summon creeper 10 64.5 -3", source).error());
        assertEquals(new PlayerState.Position(10, 64.5, -3), source.summoned.getLast());
        assertTrue(commands.execute("/summon pig", source).error());
        assertTrue(commands.execute("/summon skeleton NaN 0 0", source).error());
        source.allowSummon = false;
        assertTrue(commands.execute("/summon skeleton", source).error());
        assertEquals(List.of(MobType.ZOMBIE, MobType.CREEPER), source.types);
        assertEquals("skeleton", commands.suggest("/summon sk", 10).getFirst().text());
    }

    @Test
    void mobsReportsZonesAndSpawningCanBeToggled() {
        assertEquals(
                "Mobs: hostile surface 3/40, hostile cave 1/30; natural spawning on",
                commands.execute("/mobs", source).text());
        assertFalse(commands.execute("/mobspawning off", source).error());
        assertFalse(source.spawning);
        assertTrue(commands.execute("/mobs", source).text().endsWith("natural spawning off"));
        assertTrue(commands.execute("/mobspawning maybe", source).error());
        assertEquals("Removed 4 mobs", commands.execute("/kill mobs", source).text());
    }

    private static final class Source implements CommandSource {
        final List<PlayerState.Position> summoned = new ArrayList<>();
        final List<MobType> types = new ArrayList<>();
        boolean allowSummon = true, spawning = true;

        @Override
        public Optional<PlayerState.Position> playerPosition() {
            return Optional.of(new PlayerState.Position(1, 2, 3));
        }

        @Override
        public boolean summon(MobType type, PlayerState.Position position) {
            if (!allowSummon) return false;
            types.add(type);
            summoned.add(position);
            return true;
        }

        @Override
        public MobCensus mobs() {
            return new MobCensus(
                    List.of(
                            new MobCensus.Zone(new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE), 3, 40),
                            new MobCensus.Zone(new PopulationKey(MobCategory.HOSTILE, SpawnZone.CAVE), 1, 30)),
                    spawning);
        }

        @Override
        public void setMobSpawning(boolean enabled) {
            spawning = enabled;
        }

        @Override
        public int removeMobs() {
            return 4;
        }

        public long seed() {
            return 0;
        }

        public boolean setMode(PlayerController.Mode mode) {
            return false;
        }

        public int give(byte block, int count) {
            return count;
        }

        public WorldTime worldTime() {
            return WorldTime.INITIAL;
        }

        public void setTime(int minecraftTime) {}

        public double tickRate() {
            return 60;
        }

        public void setTickRate(double rate) {}

        public WeatherType weather() {
            return WeatherType.CLEAR;
        }

        public void setWeather(WeatherType type) {}
    }
}
