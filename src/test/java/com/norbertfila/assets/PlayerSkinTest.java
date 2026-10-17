package com.norbertfila.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PlayerSkinTest {
    @Test
    void defaultHasMirroredLegacyLimbsAndTransparentOuterLayers() {
        var pixels = SkinLoader.loadDefault().rgba();
        int[][] regions = {
            {4, 16, 20, 48, 4, 4}, {8, 16, 24, 48, 4, 4}, {8, 20, 16, 52, 4, 12},
            {4, 20, 20, 52, 4, 12}, {0, 20, 24, 52, 4, 12}, {12, 20, 28, 52, 4, 12},
            {44, 16, 36, 48, 4, 4}, {48, 16, 40, 48, 4, 4}, {48, 20, 32, 52, 4, 12},
            {44, 20, 36, 52, 4, 12}, {40, 20, 40, 52, 4, 12}, {52, 20, 44, 52, 4, 12}
        };
        for (int[] r : regions)
            for (int y = 0; y < r[5]; y++)
                for (int x = 0; x < r[4]; x++)
                    for (int c = 0; c < 4; c++)
                        assertEquals(
                                pixels[((r[1] + y) * 64 + r[0] + r[4] - 1 - x) * 4 + c],
                                pixels[((r[3] + y) * 64 + r[2] + x) * 4 + c]);
        for (int y = 32; y < 64; y++)
            for (int x = 0; x < 64; x++) if (y < 48 || x < 16 || x >= 48) assertEquals(0, pixels[(y * 64 + x) * 4 + 3]);
    }

    @Test
    void preservesTopLeftRgbaAndOwnsPixelData() throws IOException {
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 2, 0x7f123456);
        image.setRGB(1, 61, 0xffabcdef);
        var skin = SkinLoader.read(png(image));
        var rgba = skin.rgba();
        assertEquals(0x12, Byte.toUnsignedInt(rgba[(2 * 64 + 1) * 4]));
        assertEquals(0x7f, Byte.toUnsignedInt(rgba[(2 * 64 + 1) * 4 + 3]));
        assertEquals(0xab, Byte.toUnsignedInt(rgba[(61 * 64 + 1) * 4]));
        rgba[(2 * 64 + 1) * 4] = 0;
        assertEquals(0x12, Byte.toUnsignedInt(skin.rgba()[(2 * 64 + 1) * 4]));
        var owned = new PlayerSkin(rgba);
        rgba[0] = 99;
        assertEquals(0, owned.rgba()[0]);
    }

    @Test
    void rejectsLegacyDimensionsNonRgbaAndCorruptImages() throws IOException {
        var legacy = png(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB));
        var rgb = png(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB));
        assertThrows(IOException.class, () -> SkinLoader.read(legacy));
        assertThrows(IOException.class, () -> SkinLoader.read(rgb));
        assertThrows(IOException.class, () -> SkinLoader.read(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        assertThrows(IllegalArgumentException.class, () -> new PlayerSkin(new byte[64 * 32 * 4]));
    }

    private static ByteArrayInputStream png(BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return new ByteArrayInputStream(output.toByteArray());
    }
}
