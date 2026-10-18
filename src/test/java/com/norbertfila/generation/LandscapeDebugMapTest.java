package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LandscapeDebugMapTest {
    @TempDir
    Path directory;

    @Test
    void writesAlignedDeterministicMapsForTuning() throws Exception {
        var terrain = new TerrainGenerator(7340215L, TerrainConfig.defaults());
        LandscapeDebugMap.write(directory, terrain, 32, 256);
        for (String name :
                new String[] {"classicness", "trees", "vegetation", "flowers", "hills", "mountains", "biomes", "height"
                }) {
            var map = ImageIO.read(directory.resolve(name + ".png").toFile());
            assertEquals(32, map.getWidth());
            assertEquals(32, map.getHeight());
            var colors = new HashSet<Integer>();
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) colors.add(map.getRGB(x, z));
            if (name.equals("mountains")) assertEquals(java.util.Set.of(0xffffffff), colors);
            else assertTrue(colors.size() > 2, name + " must show regional variation");
        }
        byte[] before = Files.readAllBytes(directory.resolve("classicness.png"));
        LandscapeDebugMap.write(directory, new TerrainGenerator(7340215L, TerrainConfig.defaults()), 32, 256);
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("classicness.png")));
    }
}
