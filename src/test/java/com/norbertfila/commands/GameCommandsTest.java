package com.norbertfila.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerController;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GameCommandsTest {
    private final GameCommands commands = new GameCommands();
    private final Source source = new Source();

    @Test
    void suggestsCommandsAndBiomesWithReplacementRanges() {
        assertEquals("locate", commands.suggest("/lo", 3).getFirst().text());
        var suggestion = commands.suggest("/locate biome flo", 17).getFirst();
        assertEquals("floating_isles_classic", suggestion.text());
        assertEquals(14, suggestion.start());
        assertEquals(17, suggestion.end());
        assertTrue(commands.suggest("hello", 5).isEmpty());
        assertTrue(commands.usage("/tp ").contains("<x>"));
        assertTrue(commands.execute("/help", source).text().contains("locate biome"));
        for (String text : new String[] {"/tp 1 2", "/tp 1 2 3 extra", "/tp NaN 2 3", "/locate biome missing"})
            assertTrue(commands.execute(text, source).error(), text);
    }

    private static final class Source implements CommandSource {
        final Inventory inventory = new Inventory();
        PlayerController.Mode mode = PlayerController.Mode.SURVIVAL;
        boolean allowMode = true;
        boolean allowRift;
        long seed;

        @Override
        public long seed() {
            return seed;
        }

        @Override
        public boolean setMode(PlayerController.Mode value) {
            if (!allowMode) return false;
            mode = value;
            return true;
        }

        @Override
        public int give(byte block, int count) {
            return inventory.insert(Byte.toUnsignedInt(block), count);
        }

        @Override
        public boolean locateRift() {
            return allowRift;
        }
    }

    @Test
    void executesSupportedCommandsAndGeneratesHelp() {
        assertFalse(commands.execute("/gamemode creative", source).error());
        assertEquals(PlayerController.Mode.CREATIVE, source.mode);
        assertFalse(commands.execute("/give @s stone", source).error());
        assertEquals(1, source.inventory.stack(0).orElseThrow().count());
        assertFalse(commands.execute("/give @s stone 64", source).error());
        assertEquals(64, source.inventory.stack(0).orElseThrow().count());
        assertEquals(1, source.inventory.stack(1).orElseThrow().count());
        var help = commands.execute("/help", source);
        assertFalse(help.error());
        assertTrue(help.text().contains("gamemode creative"));
        assertTrue(help.text().contains("give @s <item>"));
        assertTrue(help.text().contains("seed"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 7340215, -123456789, Long.MIN_VALUE, Long.MAX_VALUE})
    void seedReportsTheFullSignedWorldSeed(long seed) {
        source.seed = seed;
        var reply = commands.execute("/seed", source);
        assertFalse(reply.error());
        assertEquals("Seed: " + seed, reply.text());
    }

    @Test
    void invalidCommandsHaveNoSideEffects() {
        for (String text : new String[] {
            "/",
            "/kill",
            "/seed extra",
            "/gamemode adventure",
            "/gamemode creative extra",
            "/give @a stone",
            "/give @s diamond 64",
            "/give @s air",
            "/give @s stone 0",
            "/give @s stone 65",
            "/give @s stone nope",
            "/give @s stone 1 extra"
        }) {
            assertTrue(commands.execute(text, source).error(), text);
            assertTrue(source.inventory.stack(0).isEmpty(), text);
            assertEquals(PlayerController.Mode.SURVIVAL, source.mode, text);
        }
        source.allowMode = false;
        assertTrue(commands.execute("/gamemode creative", source).error());
    }

    @Test
    void reportsPartialInsertionWithoutOverwritingInventory() {
        source.inventory.insert(BlockId.STONE, Inventory.SLOTS * Inventory.MAX_STACK - 2);
        var result = commands.execute("/give @s stone 64", source);
        assertFalse(result.error());
        assertTrue(result.text().contains("Added 2 x stone"));
        assertTrue(result.text().contains("no space for 62"));
        assertTrue(commands.execute("/give @s dirt", source).text().contains("Added 0"));
        assertEquals(BlockId.STONE, source.inventory.stack(35).orElseThrow().itemId());
    }

    @Test
    void locatesRiftsAndIncludesThemInHelpAndCompletion() {
        assertEquals("rift", commands.suggest("/locate r", 9).getFirst().text());
        assertTrue(commands.execute("/help", source).text().contains("locate rift"));
        assertTrue(commands.execute("/locate rift", source).error());
        source.allowRift = true;
        var reply = commands.execute("/locate rift", source);
        assertFalse(reply.error());
        assertEquals("Locating rift...", reply.text());
        assertTrue(commands.execute("/locate rift extra", source).error());
    }
}
