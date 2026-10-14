package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.ui.screens.InventoryScreen;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class UiAtlasTest {
    @Test
    void generatedAtlasMatchesShippedPixelsAndKeepsFontUnchanged() throws Exception {
        var generated = GenerateHudAtlas.generate();
        byte[] pixels = UiAtlas.defaultAtlas().pixels();
        try (var stream = getClass().getResourceAsStream("/assets/ui/font.png")) {
            var font = ImageIO.read(stream);
            for (int y = 0; y < UiAtlas.HEIGHT; y++)
                for (int x = 0; x < UiAtlas.WIDTH; x++) {
                    int offset = (y * UiAtlas.WIDTH + x) * 4;
                    int rgba = (pixels[offset + 3] & 255) << 24
                            | (pixels[offset] & 255) << 16
                            | (pixels[offset + 1] & 255) << 8
                            | pixels[offset + 2] & 255;
                    assertEquals(generated.getRGB(x, y), rgba);
                    if (y < font.getHeight()) assertEquals(font.getRGB(x, y), rgba);
                }
        }
        pixels[3] = 0;
        assertEquals(-1, UiAtlas.defaultAtlas().pixels()[3]);
    }

    @Test
    void allCatalogBlocksHaveDistinctNonemptyTransparentIconsAndUnknownIdsUseFallback() {
        byte[] pixels = UiAtlas.defaultAtlas().pixels();
        var regions = new HashSet<UiAtlas.Region>();
        for (byte block : InventoryScreen.catalogBlocks()) {
            var region = UiAtlas.item(block);
            assertTrue(regions.add(region));
            assertNotEquals(UiAtlas.item((byte) 0), region);
            int visible = 0, transparent = 0;
            for (int y = region.y(); y < region.y() + region.height(); y++)
                for (int x = region.x(); x < region.x() + region.width(); x++) {
                    if (pixels[(y * UiAtlas.WIDTH + x) * 4 + 3] == 0) transparent++;
                    else visible++;
                }
            assertTrue(visible > 0, "Empty icon: " + block);
            assertTrue(transparent > 0, "Icon has no transparent background: " + block);
        }
        assertEquals(UiAtlas.LAST_ITEM, regions.size());
        assertEquals(UiAtlas.item((byte) 0), UiAtlas.item((byte) 255));
    }

    @Test
    void validatesResourceDimensionsWhiteTexelAndRegionBounds() throws Exception {
        assertThrows(IOException.class, () -> UiAtlas.load(null));
        assertThrows(IOException.class, () -> UiAtlas.load(new ByteArrayInputStream(new byte[] {1})));
        assertThrows(
                IOException.class, () -> UiAtlas.load(png(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))));
        var image = GenerateHudAtlas.generate();
        image.setRGB(0, 0, 0);
        assertThrows(IOException.class, () -> UiAtlas.load(png(image)));
        assertThrows(IllegalArgumentException.class, () -> new UiAtlas.Region(255, 0, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new UiAtlas.Region(0, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> UiAtlas.digit(10));
    }

    private static ByteArrayInputStream png(BufferedImage image) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
