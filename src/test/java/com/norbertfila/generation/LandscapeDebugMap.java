package com.norbertfila.generation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;

public final class LandscapeDebugMap {
    private static final Map<String, ToDoubleFunction<LandscapeSample>> LAYERS = Map.of(
            "classicness",
            LandscapeSample::classicness,
            "trees",
            sample -> sample.trees() / 2,
            "vegetation",
            sample -> sample.vegetation() / 2,
            "flowers",
            sample -> sample.flowers() / 2,
            "hills",
            LandscapeSample::hills,
            "mountains",
            LandscapeSample::mountains);

    private LandscapeDebugMap() {}

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? Path.of("build/landscape-debug") : Path.of(args[0]);
        long seed = args.length < 2 ? 7340215L : Long.parseLong(args[1]);
        write(output, new TerrainGenerator(seed, TerrainConfig.defaults()), 512, 16);
    }

    static void write(Path directory, TerrainGenerator terrain, int size, int step) throws IOException {
        if (size < 2 || size > 1024 || step < 1) throw new IllegalArgumentException("Invalid map size or step");
        int origin = -Math.multiplyExact(size, step) / 2;
        Files.createDirectories(directory);
        var images = new LinkedHashMap<String, BufferedImage>();
        for (String name : LAYERS.keySet()) images.put(name, image(size));
        images.put("biomes", image(size));
        var heightMap = image(size);
        int[] heights = new int[size * size];
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        int classic = 0, modern = 0;
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++) {
                int wx = origin + x * step, wz = origin + z * step;
                var landscape = terrain.landscapeAt(wx, wz);
                var ground = terrain.sample(wx, wz);
                for (var layer : LAYERS.entrySet())
                    images.get(layer.getKey())
                            .setRGB(x, z, gray(layer.getValue().applyAsDouble(landscape)));
                images.get("biomes")
                        .setRGB(x, z, BiomeDebugMap.color(ground.biome()).getRGB());
                int height = ground.surfaceHeight();
                heights[z * size + x] = height;
                min = Math.min(min, height);
                max = Math.max(max, height);
                if (landscape.classicness() >= .75) classic++;
                if (landscape.classicness() <= .25) modern++;
            }
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++)
                heightMap.setRGB(x, z, gray((heights[z * size + x] - min) / (double) Math.max(1, max - min)));
        images.put("height", heightMap);
        for (var entry : images.entrySet())
            if (!ImageIO.write(
                    entry.getValue(),
                    "png",
                    directory.resolve(entry.getKey() + ".png").toFile()))
                throw new IOException("PNG writer is unavailable");
        System.out.printf(
                java.util.Locale.ROOT,
                "seed=%d version=%d classic=%.1f%% modern=%.1f%% transition=%.1f%% height=%d..%d%n",
                terrain.seed(),
                terrain.generatorVersion(),
                classic * 100d / heights.length,
                modern * 100d / heights.length,
                (heights.length - classic - modern) * 100d / heights.length,
                min,
                max);
    }

    private static BufferedImage image(int size) {
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    }

    private static int gray(double value) {
        int channel = (int) Math.round(Math.clamp(value, 0, 1) * 255);
        return channel * 0x010101;
    }
}
