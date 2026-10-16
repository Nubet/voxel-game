package com.norbertfila.generation;

import com.norbertfila.generation.biome.BiomeType;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class BiomeDebugMap {
    private static final int SIZE = 512;
    private static final int WORLD_STEP = 8;

    private BiomeDebugMap() {}

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? Path.of("build/biome-debug/biomes.png") : Path.of(args[0]);
        long seed = args.length < 2 ? 7340215L : Long.parseLong(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        write(output, new TerrainGenerator(seed, TerrainConfig.defaults()));
    }

    static void write(Path output, TerrainSource terrain) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        int origin = -(SIZE * WORLD_STEP) / 2;
        for (int z = 0; z < SIZE; z++)
            for (int x = 0; x < SIZE; x++) {
                BiomeType biome = terrain.sample(origin + x * WORLD_STEP, origin + z * WORLD_STEP)
                        .biome();
                image.setRGB(x, z, color(biome).getRGB());
            }
        if (!ImageIO.write(image, "png", output.toFile())) throw new IOException("PNG writer is unavailable");
    }

    static Color color(BiomeType biome) {
        return switch (biome) {
            case PLAINS -> new Color(145, 205, 95);
            case FOREST -> new Color(35, 125, 55);
            case DESERT -> new Color(224, 190, 100);
            case TAIGA -> new Color(75, 125, 135);
            case SWAMP -> new Color(65, 105, 75);
            case BADLANDS -> new Color(170, 90, 55);
            case ALPINE -> new Color(220, 230, 235);
        };
    }
}
