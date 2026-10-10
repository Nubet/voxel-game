package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.ChunkPos;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenePresetTest {
    @Test
    void sceneHashesAreDeterministicForIdenticalInputs() {
        List<ChunkPos> positions = List.of(new ChunkPos(-2, -1, 3), new ChunkPos(0, 0, 0), new ChunkPos(4, 1, -5));

        for (ScenePreset preset : ScenePreset.values()) {
            for (ChunkPos position : positions) {
                int first = Arrays.hashCode(preset.generate(position, 42L).blocks());
                int second = Arrays.hashCode(preset.generate(position, 42L).blocks());
                assertEquals(first, second, preset + " " + position);
            }
        }
    }
}
