package com.norbertfila.render;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/** Generates the project's original, seamless cloud mask. */
public final class GenerateCloudTexture {
    public static void main(String[] args) throws Exception {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7340215);
        for (int cloud = 0; cloud < 55; cloud++) {
            int cx = random.nextInt(size), cz = random.nextInt(size);
            int rx = 3 + random.nextInt(5), rz = 2 + random.nextInt(4);
            for (int z = -rz; z <= rz; z++)
                for (int x = -rx; x <= rx; x++) {
                    if (x * x / (double) (rx * rx) + z * z / (double) (rz * rz) <= 1)
                        image.setRGB(Math.floorMod(cx + x, size), Math.floorMod(cz + z, size), 0xffffffff);
                }
        }
        Path path = Path.of("src/main/resources/assets/textures/environment/clouds.png");
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }
}
