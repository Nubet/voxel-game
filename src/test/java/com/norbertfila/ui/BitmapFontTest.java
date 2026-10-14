package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BitmapFontTest {
    @Test
    void measuresSpacesLinesAndCodePointFallbackConsistently() {
        BitmapFont font = BitmapFont.DEFAULT;
        assertEquals(0, font.width(""));
        assertEquals(4, font.width(" "));
        assertEquals(font.advance('A') + 4 + font.advance('i'), font.width("A i"));
        assertEquals(Math.max(font.width("Settings"), font.width("12")), font.width("Settings\n12"));
        assertEquals(font.width("?"), font.width("\uD83C\uDF32"));
        assertEquals(font.width("?"), font.width("\u2603"));
        assertEquals('?', font.glyph(0));
        assertEquals(0, font.glyphWidth(' '));
        for (int character = 33; character <= 126; character++) {
            assertEquals(character, font.glyph(character));
            assertTrue(font.glyphWidth(character) > 0 && font.glyphWidth(character) <= BitmapFont.CELL_SIZE);
        }
    }

    @Test
    void decodedAtlasHasWhiteTexelBinaryAlphaAndDetachedStorage() {
        BitmapFont font = BitmapFont.DEFAULT;
        byte[] pixels = font.atlasPixels();
        assertEquals(BitmapFont.ATLAS_WIDTH * BitmapFont.ATLAS_HEIGHT * 4, pixels.length);
        assertArrayEquals(new byte[] {-1, -1, -1, -1}, java.util.Arrays.copyOf(pixels, 4));
        int transparent = 0;
        for (int offset = 3; offset < pixels.length; offset += 4) {
            assertTrue(pixels[offset] == 0 || pixels[offset] == -1);
            if (pixels[offset] == 0) transparent++;
        }
        assertTrue(transparent > pixels.length / 8);
        pixels[3] = 0;
        assertEquals(-1, font.atlasPixels()[3]);
    }

    @Test
    void rejectsMissingMalformedAndWrongSizedImages() throws Exception {
        assertThrows(IOException.class, () -> BitmapFont.load(null));
        assertThrows(IOException.class, () -> BitmapFont.load(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        assertThrows(
                IOException.class, () -> BitmapFont.load(png(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))));
    }

    @Test
    void rejectsMissingWhiteTexelAndFallbackGlyph() throws Exception {
        BufferedImage image = atlasImage();
        image.setRGB(0, 0, 0);
        assertThrows(IOException.class, () -> BitmapFont.load(png(image)));
        BufferedImage missingFallback = atlasImage();
        for (int y = 3 * 16; y < 4 * 16; y++) for (int x = 15 * 16; x < 16 * 16; x++) missingFallback.setRGB(x, y, 0);
        assertThrows(IOException.class, () -> BitmapFont.load(png(missingFallback)));
    }

    @Test
    void rejectsAntialiasedGlyphPixels() throws Exception {
        BufferedImage image = atlasImage();
        image.setRGB(0, 32, 0x80ffffff);
        assertThrows(IOException.class, () -> BitmapFont.load(png(image)));
    }

    private static BufferedImage atlasImage() throws IOException {
        try (var stream = BitmapFont.class.getResourceAsStream("/assets/ui/font.png")) {
            return ImageIO.read(stream);
        }
    }

    private static ByteArrayInputStream png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
