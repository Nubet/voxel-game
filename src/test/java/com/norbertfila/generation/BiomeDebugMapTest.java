package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BiomeDebugMapTest {
    @Test
    void writesAReadableBiomeMap() throws Exception {
        Path output = Files.createTempFile("biomes-", ".png");
        try {
            BiomeDebugMap.write(output, new TerrainGenerator(7340215L, TerrainConfig.defaults()));
            var image = ImageIO.read(output.toFile());
            assertEquals(512, image.getWidth());
            assertEquals(512, image.getHeight());
            var colors = new HashSet<Integer>();
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) colors.add(image.getRGB(x, y));
            assertTrue(colors.size() >= 2);
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
