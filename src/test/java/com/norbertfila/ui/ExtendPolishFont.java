package com.norbertfila.ui;

import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class ExtendPolishFont {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected font PNG path");
        var path = Path.of(args[0]);
        var image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() != 256 || image.getHeight() != 128)
            throw new IllegalArgumentException("Expected a 256x128 font image");
        String bases = "AaCcEeLlNnOoSsZzZz";
        for (int index = 0; index < bases.length(); index++) {
            int source = bases.charAt(index), glyph = index + 1;
            int sx = source % 16 * 16, sy = source / 16 * 16;
            int tx = glyph % 16 * 16, ty = glyph / 16 * 16;
            int width = 0, bottom = 0;
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int pixel = image.getRGB(sx + x, sy + y);
                    image.setRGB(tx + x, ty + y, pixel);
                    if (pixel >>> 24 != 0) {
                        width = Math.max(width, x + 1);
                        bottom = Math.max(bottom, y);
                    }
                }
            if (index == 0 || index == 1 || index == 4 || index == 5) {
                image.setRGB(tx + width - 2, ty + bottom + 1, 0xffffffff);
                image.setRGB(tx + width - 2, ty + bottom + 2, 0xffffffff);
                image.setRGB(tx + width - 1, ty + bottom + 2, 0xffffffff);
            } else if (index == 6 || index == 7) {
                for (int x = 0; x < Math.min(width, 5); x++) image.setRGB(tx + x, ty + 8 - x / 2, 0xffffffff);
            } else if (index >= 16) {
                image.setRGB(tx + width / 2, ty, 0xffffffff);
            } else {
                image.setRGB(tx + width / 2 + 1, ty, 0xffffffff);
                image.setRGB(tx + width / 2, ty + 1, 0xffffffff);
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }
}
