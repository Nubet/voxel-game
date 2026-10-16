package com.norbertfila.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerController;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class GameCommandsTest {
    private final GameCommands commands = new GameCommands();
    private final Source source = new Source();

    private static final class Source implements CommandSource {
        final Inventory inventory = new Inventory();
        PlayerController.Mode mode = PlayerController.Mode.SURVIVAL;
        boolean allowMode = true;

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
    }

    @Test
    void invalidCommandsHaveNoSideEffects() {
        for (String text : new String[] {
            "/",
            "/kill",
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
}
