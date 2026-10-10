package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class ChunkEntryTest {
    @Test
    void followsGenerationMeshAndGpuLifecycle() {
        ChunkEntry entry = new ChunkEntry(new ChunkPos(2, 0, -3));

        entry.beginGenerating();
        entry.generated(4);
        assertTrue(entry.beginMeshing(4));
        assertTrue(entry.readyCpu(4));
        assertEquals(0, entry.meshRevision());
        assertTrue(entry.uploaded(0));
        assertEquals(ChunkState.RESIDENT_GPU, entry.state());
    }

    @Test
    void rejectsStaleRevisionAndDuplicateUpload() {
        ChunkEntry entry = new ChunkEntry(new ChunkPos(0, 0, 0));
        entry.beginGenerating();
        entry.generated(7);

        assertFalse(entry.beginMeshing(6));
        assertTrue(entry.beginMeshing(7));
        assertFalse(entry.readyCpu(6));
        assertTrue(entry.readyCpu(7));
        assertFalse(entry.uploaded(1));
        assertTrue(entry.uploaded(0));
        assertFalse(entry.uploaded(0));
    }

    @Test
    void rejectsInvalidTransitions() {
        ChunkEntry entry = new ChunkEntry(new ChunkPos(0, 0, 0));

        assertThrows(IllegalStateException.class, () -> entry.transitionTo(ChunkState.READY_CPU));
        entry.beginGenerating();
        assertThrows(IllegalStateException.class, () -> entry.transitionTo(ChunkState.RESIDENT_GPU));
        entry.generated(0);
        entry.beginEviction();
        assertThrows(IllegalStateException.class, () -> entry.transitionTo(ChunkState.REQUESTED));
    }

    @Test
    void matchesRevisionAndGenerationToken() {
        ChunkEntry entry = new ChunkEntry(new ChunkPos(0, 0, 0));
        entry.beginGenerating();
        entry.setGenerationToken(12);
        entry.generated(5);

        assertTrue(entry.isCurrent(5, 12));
        assertFalse(entry.isCurrent(4, 12));
        assertFalse(entry.isCurrent(5, 13));
    }

    @Test
    void restoresGpuResidentStateWhenRemeshSubmissionFails() {
        ChunkEntry entry = new ChunkEntry(new ChunkPos(0, 0, 0));
        entry.beginGenerating();
        entry.generated(0);
        assertTrue(entry.beginMeshing(0));
        assertTrue(entry.readyCpu(0));
        assertTrue(entry.uploaded(0));
        assertTrue(entry.beginMeshing(0));

        assertTrue(entry.meshFailed());
        assertEquals(ChunkState.RESIDENT_GPU, entry.state());
    }

    @Test
    void restoresGeneratedStateWhenUploadFailsBeforeFirstGpuMesh() {
        ChunkEntry entry = new ChunkEntry(new ChunkPos(0, 0, 0));
        entry.beginGenerating();
        entry.generated(0);
        assertTrue(entry.beginMeshing(0));
        assertTrue(entry.readyCpu(0));

        assertTrue(entry.meshFailed());
        assertEquals(ChunkState.GENERATED, entry.state());
    }
}
