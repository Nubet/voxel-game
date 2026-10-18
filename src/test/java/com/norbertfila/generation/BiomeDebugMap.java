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
            case JUNGLE -> new Color(25, 95, 30);
            case DESERT -> new Color(224, 190, 100);
            case TAIGA -> new Color(75, 125, 135);
            case SNOWY_TAIGA -> new Color(210, 225, 230);
            case SWAMP -> new Color(65, 105, 75);
            case BADLANDS -> new Color(170, 90, 55);
            case ALPINE -> new Color(220, 230, 235);
            case OCEAN -> new Color(30, 70, 160);
            case LAKE -> new Color(55, 125, 190);
            case RIVER -> new Color(80, 160, 210);
            case STONY_SLOPES -> new Color(130, 135, 140);
            case RAINFOREST -> new Color(40, 170, 50);
            case SEASONAL_FOREST -> new Color(95, 160, 45);
            case CLASSIC_FOREST -> new Color(45, 145, 85);
            case SWAMPLAND -> new Color(95, 120, 45);
            case SAVANNA -> new Color(185, 180, 75);
            case SHRUBLAND -> new Color(165, 175, 110);
            case CLASSIC_PLAINS -> new Color(175, 220, 100);
            case CLASSIC_DESERT -> new Color(245, 215, 145);
            case TUNDRA -> new Color(235, 240, 215);
            case ARCHLANDS_CLASSIC -> new Color(140, 90, 185);
            case FLOATING_ISLES_CLASSIC -> new Color(210, 145, 240);
            case OVERHANG_HIGHLANDS_CLASSIC -> new Color(110, 85, 135);
            case HIDDEN_VALLEYS_CLASSIC -> new Color(40, 205, 150);
            case COASTAL_CLIFFS_CLASSIC -> new Color(180, 160, 150);
        };
    }
}
