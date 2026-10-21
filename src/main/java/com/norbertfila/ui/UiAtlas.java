package com.norbertfila.ui;

import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class UiAtlas {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 512;
    public static final int ITEM_Y = 256;
    public static final int ITEM_SIZE = 16;
    public static final int LAST_ITEM = 255;
    public static final Region HOTBAR = new Region(0, 176, 182, 22);
    public static final Region SELECTION = new Region(0, 200, 24, 23);
    public static final Region CROSSHAIR = new Region(32, 200, 15, 15);
    public static final Region CROSSHAIR_BLOCK = new Region(48, 200, 15, 15);
    public static final Region CROSSHAIR_ENTITY = new Region(64, 200, 15, 15);
    public static final Region CROSSHAIR_RANGED = new Region(80, 200, 15, 15);
    public static final Region CROSSHAIR_DOT = new Region(96, 200, 15, 15);
    public static final Region HEART_EMPTY = new Region(0, 232, 9, 9);
    public static final Region HEART_FULL = new Region(9, 232, 9, 9);
    public static final Region HEART_HALF = new Region(18, 232, 9, 9);
    public static final Region HEART_FLASH = new Region(27, 232, 9, 9);
    private final byte[] pixels;

    private UiAtlas(byte[] pixels) {
        this.pixels = pixels;
    }

    public static UiAtlas defaultAtlas() {
        return DefaultAtlas.INSTANCE;
    }

    private static final class DefaultAtlas {
        private static final UiAtlas INSTANCE = loadDefault();
    }

    private static UiAtlas loadDefault() {
        try (var stream = UiAtlas.class.getResourceAsStream("/assets/ui/ui.png")) {
            return load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load UI atlas", failure);
        }
    }

    static UiAtlas load(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing UI atlas");
        var image = ImageIO.read(stream);
        if (image == null || image.getWidth() != WIDTH || image.getHeight() != HEIGHT)
            throw new IOException("UI atlas must be a " + WIDTH + "x" + HEIGHT + " image");
        if (image.getRGB(0, 0) != 0xffffffff) throw new IOException("UI atlas is missing the white texel");
        byte[] pixels = new byte[WIDTH * HEIGHT * 4];
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int argb = image.getRGB(x, y);
                int offset = (y * WIDTH + x) * 4;
                pixels[offset] = (byte) (argb >> 16);
                pixels[offset + 1] = (byte) (argb >> 8);
                pixels[offset + 2] = (byte) argb;
                pixels[offset + 3] = (byte) (argb >>> 24);
            }
        return new UiAtlas(pixels);
    }

    public byte[] pixels() {
        return pixels.clone();
    }

    public static Region item(byte block) {
        int id = Byte.toUnsignedInt(block);
        if (!com.norbertfila.items.ItemId.isValid(block)) id = 0;
        return new Region(id % 16 * ITEM_SIZE, ITEM_Y + id / 16 * ITEM_SIZE, ITEM_SIZE, ITEM_SIZE);
    }

    public static Region digit(int value) {
        if (value < 0 || value > 9) throw new IllegalArgumentException("Digit must be between 0 and 9");
        return new Region(value * 4, 224, 3, 7);
    }

    public record Region(int x, int y, int width, int height) {
        public Region {
            if (x < 0 || y < 0 || width <= 0 || height <= 0 || x > WIDTH - width || y > HEIGHT - height)
                throw new IllegalArgumentException("Sprite must fit inside the UI atlas");
        }
    }
}
