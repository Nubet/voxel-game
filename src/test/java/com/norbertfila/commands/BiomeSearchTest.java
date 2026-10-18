package com.norbertfila.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BiomeSearchTest {
    @Test
    void runsOneSearchAndRejectsRequestsAfterClose() throws Exception {
        var terrain = new HillsGenerator();
        var origin = new PlayerState.Position(.5, 80, .5);
        var biome = terrain.sample(0, 0).biome();
        var searches = new BiomeSearch(terrain);
        try (searches) {
            assertTrue(searches.request(biome, origin));
            assertFalse(searches.request(biome, origin));
            GameCommands.Reply reply = null;
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (reply == null && System.nanoTime() < deadline) {
                reply = searches.poll().orElse(null);
                Thread.sleep(1);
            }
            assertNotNull(reply);
            assertFalse(reply.error());
            assertTrue(reply.text().contains("Nearest"));
            assertTrue(reply.text().contains("X=0 Z=0"));
        }
        assertFalse(searches.request(biome, origin));
        assertTrue(searches.poll().isEmpty());
    }

    @Test
    void neverCallsBudgetLimitedCandidateNearest() {
        var reply = BiomeSearch.reply(new BiomeLocator.Result(
                BiomeType.FOREST,
                BiomeLocator.Status.LIMIT_REACHED,
                Optional.of(new BiomeLocator.Match(10, 20, 70, 22)),
                100));
        assertTrue(reply.text().contains("Nearest not confirmed"));
        assertTrue(reply.text().contains("X=10 Z=20"));
        assertTrue(BiomeSearch.reply(new BiomeLocator.Result(
                        BiomeType.FOREST, BiomeLocator.Status.LIMIT_REACHED, Optional.empty(), 100))
                .text()
                .contains("incomplete"));
    }
}
