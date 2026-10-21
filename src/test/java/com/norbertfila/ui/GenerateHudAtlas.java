package com.norbertfila.ui;

import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.BlockShape;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class GenerateHudAtlas {
    private static final String[] DIGITS = {
        "111101101101101101111", "010110010010010010111", "111001001111100100111",
        "111001001111001001111", "101101101111001001001", "111100100111001001111",
        "111100100111101101111", "111001001010010010010", "111101101111101101111",
        "111101101111001001111"
    };

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected output PNG path");
        ImageIO.write(generate(), "png", Path.of(args[0]).toFile());
    }

    static BufferedImage generate() throws IOException {
        var atlas = new BufferedImage(UiAtlas.WIDTH, UiAtlas.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        copy(read("/assets/ui/font.png", 256, 128), atlas, 0, 0);
        Map<Integer, BufferedImage> textures = new HashMap<>();
        for (var entry : BlockRegistry.DEFAULT.textureLayers().entrySet()) {
            if (entry.getValue() != BlockRegistry.FALLBACK_TEXTURE)
                textures.put(entry.getValue(), read("/assets/textures/blocks/" + entry.getKey() + ".png", 16, 16));
        }
        for (var entry : textures.entrySet()) {
            int layer = entry.getKey();
            if (!com.norbertfila.generation.biome.BiomeTint.tintedTexture(layer) || layer == 3) continue;
            float[] tint = com.norbertfila.generation.biome.BiomeTint.color(
                    0, com.norbertfila.generation.biome.BiomeTint.foliageTexture(layer));
            var image = entry.getValue();
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int pixel = image.getRGB(x, y);
                    image.setRGB(
                            x,
                            y,
                            pixel & 0xff000000
                                    | (int) ((pixel >> 16 & 255) * tint[0]) << 16
                                    | (int) ((pixel >> 8 & 255) * tint[1]) << 8
                                    | (int) ((pixel & 255) * tint[2]));
                }
        }
        var side = textures.get(BlockRegistry.GRASS_SIDE_TEXTURE);
        var overlay = textures.get(BlockRegistry.GRASS_SIDE_OVERLAY_TEXTURE);
        float[] grass = com.norbertfila.generation.biome.BiomeTint.color(0, false);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int pixel = overlay.getRGB(x, y);
                if ((pixel >>> 24) < 128) continue;
                side.setRGB(
                        x,
                        y,
                        0xff000000
                                | (int) ((pixel >> 16 & 255) * grass[0]) << 16
                                | (int) ((pixel >> 8 & 255) * grass[1]) << 8
                                | (int) ((pixel & 255) * grass[2]));
            }
        for (int layer : new int[] {
            BlockRegistry.GRASS_SIDE_TEXTURE,
            BlockRegistry.DRY_GRASS_SIDE_TEXTURE,
            BlockRegistry.PODZOL_SIDE_TEXTURE,
            BlockRegistry.DEFAULT.textureLayer("refi_mycelium_side")
        }) {
            var image = textures.get(layer);
            var upright = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) upright.setRGB(x, y, image.getRGB(x, 15 - y));
            textures.put(layer, upright);
        }
        var fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 15; y++)
            for (int x = 1; x < 15; x++) fallback.setRGB(x, y, (x / 4 + y / 4) % 2 == 0 ? 0xffff00ff : 0xff181818);
        copy(fallback, atlas, 0, UiAtlas.ITEM_Y);
        for (int id = 1; id <= UiAtlas.LAST_ITEM; id++) {
            if (!com.norbertfila.world.BlockId.isValid((byte) id)) continue;
            var definition = BlockRegistry.DEFAULT.definition((byte) id);
            var icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            if (definition.shape() == BlockShape.CROSS) {
                copy(textures.get(definition.sideTexture()), icon, 0, 0);
            } else {
                int height = Math.max(1, Math.round(8 * definition.model().maxY()));
                face(icon, textures.get(definition.sideTexture()), 1, 12 - height, 7, 4, 0, height, .78);
                face(icon, textures.get(definition.sideTexture()), 8, 16 - height, 7, -4, 0, height, .6);
                face(icon, textures.get(definition.topTexture()), 8, 8 - height, 7, 4, -7, 4, 1);
            }
            var region = UiAtlas.item((byte) id);
            copy(icon, atlas, region.x(), region.y());
        }
        drawHotbar(atlas);
        drawHearts(atlas);
        var selection = UiAtlas.SELECTION;
        border(atlas, selection.x(), selection.y(), selection.width(), selection.height(), 0xfff5cc4c);
        for (int i = 0; i < 4; i++) {
            atlas.setRGB(selection.x() + i + 1, selection.y() + 1, 0xffffe39a);
            atlas.setRGB(selection.x() + selection.width() - i - 2, selection.y() + selection.height() - 2, 0xffaa8734);
        }
        crosshair(atlas, UiAtlas.CROSSHAIR, "crosshair");
        crosshair(atlas, UiAtlas.CROSSHAIR_BLOCK, "square");
        crosshair(atlas, UiAtlas.CROSSHAIR_ENTITY, "cross-open-diagonal");
        crosshair(atlas, UiAtlas.CROSSHAIR_RANGED, "cross-open");
        crosshair(atlas, UiAtlas.CROSSHAIR_DOT, "dot");
        for (int digit = 0; digit <= 9; digit++) {
            var region = UiAtlas.digit(digit);
            for (int i = 0; i < 21; i++)
                if (DIGITS[digit].charAt(i) == '1') atlas.setRGB(region.x() + i % 3, region.y() + i / 3, 0xffffffff);
        }
        return atlas;
    }

    private static void crosshair(BufferedImage atlas, UiAtlas.Region region, String name) throws IOException {
        var sprite = read("/assets/ui/crosshair/" + name + ".png", 15, 15);
        for (int y = 0; y < 15; y++)
            for (int x = 0; x < 15; x++) {
                int alpha = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if (x + dx >= 0 && x + dx < 15 && y + dy >= 0 && y + dy < 15)
                            alpha = Math.max(alpha, sprite.getRGB(x + dx, y + dy) >>> 24);
                int pixel = sprite.getRGB(x, y);
                atlas.setRGB(region.x() + x, region.y() + y, (pixel >>> 24) > 0 ? pixel : alpha << 24 | 0x141414);
            }
    }

    private static final String[] HEART = {
        ".##...##.",
        "#rr#.#rr#",
        "#rwrrrrr#",
        "#rrrrrrr#",
        ".#rrrrr#.",
        "..#rrr#..",
        "...#r#...",
        "....#....",
        "........."
    };

    private static void drawHearts(BufferedImage atlas) {
        for (int y = 0; y < HEART.length; y++)
            for (int x = 0; x < HEART[y].length(); x++) {
                char pixel = HEART[y].charAt(x);
                if (pixel == '.') continue;
                boolean outline = pixel == '#';
                int full = pixel == 'w' ? 0xffffc8c8 : 0xffd42020;
                int empty = outline ? 0xff1c0606 : 0xff3c1010;
                atlas.setRGB(UiAtlas.HEART_EMPTY.x() + x, UiAtlas.HEART_EMPTY.y() + y, empty);
                atlas.setRGB(UiAtlas.HEART_FLASH.x() + x, UiAtlas.HEART_FLASH.y() + y, outline ? 0xffffffff : empty);
                // Fill sprites leave the outline to the container so that the hurt flash stays visible.
                if (outline) continue;
                atlas.setRGB(UiAtlas.HEART_FULL.x() + x, UiAtlas.HEART_FULL.y() + y, full);
                if (x < 5) atlas.setRGB(UiAtlas.HEART_HALF.x() + x, UiAtlas.HEART_HALF.y() + y, full);
            }
    }

    private static void drawHotbar(BufferedImage image) {
        var bar = UiAtlas.HOTBAR;
        for (int y = 0; y < bar.height(); y++)
            for (int x = 0; x < bar.width(); x++) image.setRGB(bar.x() + x, bar.y() + y, 0xe6151820);
        border(image, bar.x(), bar.y(), bar.width(), bar.height(), 0xff232830);
        for (int slot = 0; slot < 9; slot++) {
            int x = bar.x() + 1 + slot * 20;
            border(image, x, bar.y() + 1, 20, 20, 0xff404a59);
            for (int i = 2; i < 18; i++) {
                image.setRGB(x + i, bar.y() + 2, 0xff171c24);
                image.setRGB(x + 1, bar.y() + i + 1, 0xff171c24);
                image.setRGB(x + i, bar.y() + 19, 0xff59616b);
            }
        }
    }

    private static void face(
            BufferedImage icon, BufferedImage source, int x, int y, int ax, int ay, int bx, int by, double shade) {
        double determinant = ax * by - ay * bx;
        for (int py = 0; py < 16; py++)
            for (int px = 0; px < 16; px++) {
                double dx = px + .5 - x, dy = py + .5 - y;
                double u = (dx * by - dy * bx) / determinant;
                double v = (ax * dy - ay * dx) / determinant;
                if (u < 0 || u >= 1 || v < 0 || v >= 1) continue;
                int argb = source.getRGB((int) (u * 16), (int) (v * 16));
                int r = (int) ((argb >> 16 & 255) * shade);
                int g = (int) ((argb >> 8 & 255) * shade);
                int b = (int) ((argb & 255) * shade);
                icon.setRGB(px, py, argb & 0xff000000 | r << 16 | g << 8 | b);
            }
    }

    private static void border(BufferedImage image, int x, int y, int width, int height, int color) {
        for (int i = 0; i < width; i++) {
            image.setRGB(x + i, y, color);
            image.setRGB(x + i, y + height - 1, color);
        }
        for (int i = 0; i < height; i++) {
            image.setRGB(x, y + i, color);
            image.setRGB(x + width - 1, y + i, color);
        }
    }

    private static BufferedImage read(String resource, int width, int height) throws IOException {
        try (var stream = GenerateHudAtlas.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing texture: " + resource);
            var image = ImageIO.read(stream);
            if (image == null || image.getWidth() != width || image.getHeight() != height)
                throw new IOException("Unexpected texture dimensions: " + resource);
            return image;
        }
    }

    private static void copy(BufferedImage source, BufferedImage target, int x, int y) {
        for (int py = 0; py < source.getHeight(); py++)
            for (int px = 0; px < source.getWidth(); px++) target.setRGB(x + px, y + py, source.getRGB(px, py));
    }
}
