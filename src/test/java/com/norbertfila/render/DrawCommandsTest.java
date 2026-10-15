package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.ChunkPos;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DrawCommandsTest {
    @Test
    void writesIndirectCommandsWithBaseInstanceIndexingTheChunkTable() {
        var commands = new DrawCommands();
        commands.add(0, 5, 12, 36, new ChunkPos(-1, 2, -3));
        commands.add(0, 5, 60, 6, new ChunkPos(4, 0, 9));
        assertArrayEquals(new int[] {36, 1, 12, 0, 6, 1, 60, 1}, Arrays.copyOf(commands.commands(), 8));
        assertArrayEquals(new int[] {-1, 2, -3, 0, 4, 0, 9, 0}, Arrays.copyOf(commands.chunks(), 8));
    }

    @Test
    void startsANewRunWhenThePassOrArenaBufferChanges() {
        var commands = new DrawCommands();
        commands.add(0, 5, 0, 6, new ChunkPos(0, 0, 0));
        commands.add(0, 5, 6, 6, new ChunkPos(1, 0, 0));
        commands.add(0, 7, 0, 6, new ChunkPos(2, 0, 0));
        commands.add(2, 7, 6, 6, new ChunkPos(3, 0, 0));
        assertEquals(3, commands.runs());
        assertEquals(2, commands.runCount(0));
        assertEquals(7, commands.runBuffer(1));
        assertEquals(2, commands.runPass(2));
        assertEquals(3, commands.runStart(2));
    }

    @Test
    void reportsChangesOnlyWhenTheFrameDiffers() {
        var commands = new DrawCommands();
        commands.add(0, 5, 0, 6, new ChunkPos(0, 0, 0));
        assertTrue(commands.changedSinceLastUpload());
        commands.clear();
        commands.add(0, 5, 0, 6, new ChunkPos(0, 0, 0));
        assertFalse(commands.changedSinceLastUpload());
        commands.clear();
        commands.add(0, 5, 0, 12, new ChunkPos(0, 0, 0));
        assertTrue(commands.changedSinceLastUpload());
        commands.clear();
        assertTrue(commands.changedSinceLastUpload());
        assertFalse(commands.changedSinceLastUpload());
    }

    @Test
    void growsBeyondItsInitialCapacity() {
        var commands = new DrawCommands();
        for (int draw = 0; draw < 5_000; draw++)
            commands.add(draw % 3, draw % 2, draw * 6, 6, new ChunkPos(draw, 0, 0));
        assertEquals(5_000, commands.draws());
        assertEquals(4_999, commands.commands()[4_999 * 4 + 3]);
        assertEquals(4_999, commands.chunks()[4_999 * 4]);
    }

    @Test
    void rejectsEmptyDraws() {
        assertThrows(IllegalArgumentException.class, () -> new DrawCommands().add(0, 1, 0, 0, new ChunkPos(0, 0, 0)));
    }
}
