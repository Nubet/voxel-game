package com.norbertfila.render;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/** Generates the project's original, seamless cloud mask. */
public final class GenerateCloudTexture {
    private static final int SIZE = 64;
    private static final int CLOUD_COUNT = 14;
    private static final int GAP = 2;
    private static final int FORMATION_SIZE = 24;

    public static void main(String[] args) throws Exception {
        Path path = Path.of("src/main/resources/assets/textures/environment/clouds.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(generate(), "png", path.toFile());
    }

    static BufferedImage generate() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7340215);
        int placed = 0;
        for (int attempt = 0; attempt < 2000 && placed < CLOUD_COUNT; attempt++) {
            int x = random.nextInt(SIZE), z = random.nextInt(SIZE);
            boolean[][] formation = formation(random, 2 + placed % 4);
            if (!hasSpace(image, x, z, formation)) continue;
            for (int dz = 0; dz < FORMATION_SIZE; dz++)
                for (int dx = 0; dx < FORMATION_SIZE; dx++)
                    if (formation[dz][dx])
                        image.setRGB(Math.floorMod(x + dx, SIZE), Math.floorMod(z + dz, SIZE), 0xffffffff);
            placed++;
        }
        if (placed != CLOUD_COUNT) throw new IllegalStateException("Cloud formations do not fit the tile");
        return image;
    }

    private static boolean[][] formation(Random random, int lobes) {
        boolean[][] mask = new boolean[FORMATION_SIZE][FORMATION_SIZE];
        int[][] patches = new int[lobes][4];
        for (int lobe = 0; lobe < lobes; lobe++) {
            int width = lobe == 0 ? 4 + random.nextInt(2) : 3 + random.nextInt(2);
            int depth = 3 + random.nextInt(2);
            int x = 5, z = 5;
            if (lobe > 0) {
                int[] parent = patches[random.nextInt(lobe)];
                x = parent[0] + random.nextInt(parent[2] + width - 3) - width + 2;
                z = parent[1] + random.nextInt(parent[3] + depth - 3) - depth + 2;
                switch (random.nextInt(4)) {
                    case 0 -> x = parent[0] + parent[2] - 2;
                    case 1 -> x = parent[0] - width + 2;
                    case 2 -> z = parent[1] + parent[3] - 2;
                    case 3 -> z = parent[1] - depth + 2;
                }
            }
            x = Math.clamp(x, 0, FORMATION_SIZE / 2 - width);
            z = Math.clamp(z, 0, FORMATION_SIZE / 2 - depth);
            patches[lobe] = new int[] {x, z, width, depth};
            // Two-pixel cells keep branches broad and connected instead of producing isolated tips.
            for (int dz = 0; dz < depth; dz++)
                for (int dx = 0; dx < width; dx++) {
                    if ((dx == 0 || dx == width - 1) && (dz == 0 || dz == depth - 1)) continue;
                    for (int oz = 0; oz < 2; oz++)
                        for (int ox = 0; ox < 2; ox++) mask[(z + dz) * 2 + oz][(x + dx) * 2 + ox] = true;
                }
        }
        return mask;
    }

    private static boolean hasSpace(BufferedImage image, int x, int z, boolean[][] formation) {
        // Check across tile seams too, so repeated clouds cannot merge into a larger formation.
        for (int dz = 0; dz < FORMATION_SIZE; dz++)
            for (int dx = 0; dx < FORMATION_SIZE; dx++) {
                if (!formation[dz][dx]) continue;
                for (int oz = -GAP; oz <= GAP; oz++)
                    for (int ox = -GAP; ox <= GAP; ox++)
                        if (image.getRGB(Math.floorMod(x + dx + ox, SIZE), Math.floorMod(z + dz + oz, SIZE)) != 0)
                            return false;
            }
        return true;
    }
}
