package com.norbertfila.ui;

import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public final class BitmapFont {
    public static final int ATLAS_WIDTH = 256;
    public static final int ATLAS_HEIGHT = 128;
    public static final int CELL_SIZE = 16;
    public static final int GLYPH_HEIGHT = 14;
    public static final int LINE_HEIGHT = 16;
    public static final BitmapFont DEFAULT = loadDefault();
    private final int[] widths;

    private BitmapFont(int[] widths) {
        this.widths = widths;
    }

    private static BitmapFont loadDefault() {
        String resource = "/assets/ui/font.png";
        try (InputStream stream = BitmapFont.class.getResourceAsStream(resource)) {
            return load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load UI atlas: " + resource, failure);
        }
    }

    static BitmapFont load(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing UI atlas");
        var image = ImageIO.read(stream);
        if (image == null || image.getWidth() != ATLAS_WIDTH || image.getHeight() != ATLAS_HEIGHT)
            throw new IOException("UI atlas must be a 256x128 image");
        if (image.getRGB(0, 0) != 0xffffffff) throw new IOException("UI atlas is missing the white texel");
        int[] widths = new int[128];
        for (int y = 0; y < ATLAS_HEIGHT; y++)
            for (int x = 0; x < ATLAS_WIDTH; x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha != 0 && argb != 0xffffffff)
                    throw new IOException("UI atlas pixels must be white or transparent");
                int character = y / CELL_SIZE * 16 + x / CELL_SIZE;
                if (alpha != 0) {
                    if (character >= 32 && y % CELL_SIZE >= GLYPH_HEIGHT)
                        throw new IOException("Glyph exceeds font height: " + character);
                    widths[character] = Math.max(widths[character], x % CELL_SIZE + 1);
                }
            }
        if (widths[' '] != 0) throw new IOException("Space glyph must be empty");
        for (int character = 33; character <= 126; character++)
            if (widths[character] == 0) throw new IOException("UI atlas is missing glyph " + character);
        return new BitmapFont(widths);
    }

    public int glyph(int codePoint) {
        return codePoint >= 32 && codePoint <= 126 ? codePoint : '?';
    }

    public int glyphWidth(int codePoint) {
        return widths[glyph(codePoint)];
    }

    public int advance(int codePoint) {
        return codePoint == ' ' ? 4 : glyphWidth(codePoint) + 1;
    }

    public int width(String text) {
        int line = 0, maximum = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                maximum = Math.max(maximum, line);
                line = 0;
            } else line = Math.addExact(line, advance(codePoint));
        }
        return Math.max(maximum, line);
    }
}
