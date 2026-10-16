package com.norbertfila.assets;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockRegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AssetLoaderTest {
    @Test
    void plantAssetsAreColoredCutoutsAndDecodeExactlyWithoutFallback() throws IOException {
        TextureSet textures = new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures();
        for (String name : java.util.List.of(
                "refi_short_grass", "refi_fern", "refi_dandelion_yellow", "refi_dandelion_white", "refi_poppy")) {
            var texture = textures.layer(BlockRegistry.DEFAULT.textureLayer(name));
            try (var input = AssetLoaderTest.class.getResourceAsStream("/assets/textures/blocks/" + name + ".png")) {
                assertNotNull(input);
                BufferedImage image = ImageIO.read(input);
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
                int opaque = 0, transparent = 0, colored = 0;
                for (int y = 0; y < 16; y++)
                    for (int x = 0; x < 16; x++) {
                        int pixel = image.getRGB(x, y);
                        for (int c = 0; c < 4; c++)
                            assertEquals((pixel >>> (c == 3 ? 24 : 16 - c * 8)) & 255, texture.rgbaAt(x, y, c));
                        if ((pixel >>> 24) >= 128) {
                            opaque++;
                            if (((pixel >>> 16) & 255) != ((pixel >>> 8) & 255)) colored++;
                        } else transparent++;
                    }
                assertTrue(opaque > 0 && transparent > 0 && colored > 0, name);
                assertTrue(
                        java.util.stream.IntStream.range(0, 16).anyMatch(x -> (image.getRGB(x, 15) >>> 24) >= 128),
                        name + " must reach the ground without a renderer offset");
            }
        }
    }

    @Test
    void loadsAllDefaultImagesInRegistryLayerOrderAsIndependentRgba() {
        TextureSet textures = new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures();
        assertEquals(BlockRegistry.DEFAULT.textureLayers().size(), textures.layerCount());
        for (TextureSet.Texture texture : textures.layers()) {
            assertEquals(16, texture.width());
            assertEquals(16, texture.height());
            assertEquals(16 * 16 * 4, texture.rgba().length);
        }
        assertEquals(255, textures.layer(BlockRegistry.FALLBACK_TEXTURE).rgbaAt(0, 0, 0));
        assertEquals(0, textures.layer(BlockRegistry.FALLBACK_TEXTURE).rgbaAt(0, 0, 1));
        assertNotEquals(
                BlockRegistry.DEFAULT.textureLayer("refi_grass_top"), BlockRegistry.DEFAULT.textureLayer("refi_stone"));
        byte[] copy = textures.layer(BlockRegistry.GRASS_TOP_TEXTURE).rgba();
        copy[0] = 0;
        assertNotEquals(0, textures.layer(BlockRegistry.GRASS_TOP_TEXTURE).rgbaAt(0, 0, 0));
    }

    @Test
    void shippedWaterDecodesWithoutFallback() throws IOException {
        TextureSet.Texture water =
                new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures().layer(BlockRegistry.WATER_TEXTURE);
        try (var input = AssetLoaderTest.class.getResourceAsStream("/assets/textures/blocks/refi_water.png")) {
            assertNotNull(input);
            BufferedImage expected = ImageIO.read(input);
            assertEquals(16, expected.getWidth());
            assertEquals(16, expected.getHeight());
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int pixel = expected.getRGB(x, y);
                    assertEquals((pixel >>> 16) & 255, water.rgbaAt(x, y, 0));
                    assertEquals((pixel >>> 8) & 255, water.rgbaAt(x, y, 1));
                    assertEquals(pixel & 255, water.rgbaAt(x, y, 2));
                    assertEquals(pixel >>> 24, water.rgbaAt(x, y, 3));
                }
        }
    }

    @Test
    void missingOrCorruptPngUsesOpaqueMagentaBlackCheckerboard() {
        AssetLoader missing = new AssetLoader(BlockRegistry.DEFAULT, resource -> null);
        TextureSet missingTextures = missing.loadBlockTextures();
        assertCheckerboard(missingTextures.layer(BlockRegistry.GRASS_TOP_TEXTURE));

        AssetLoader corrupt = new AssetLoader(
                BlockRegistry.DEFAULT,
                resource -> new ByteArrayInputStream("not a PNG".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertCheckerboard(corrupt.loadBlockTextures().layer(BlockRegistry.STONE_TEXTURE));
    }

    @Test
    void wrongDimensionsFallBackWithoutLeakingStbImageMemory() throws IOException {
        byte[] png = png(8, 16);
        AssetLoader loader = new AssetLoader(BlockRegistry.DEFAULT, resource -> new ByteArrayInputStream(png));
        assertCheckerboard(loader.loadBlockTextures().layer(BlockRegistry.DIRT_TEXTURE));
    }

    @Test
    void decodedPixelsAreRgbaWithStableTopToBottomRows() throws IOException {
        byte[] png = png(16, 16);
        AssetLoader loader = new AssetLoader(BlockRegistry.DEFAULT, resource -> new ByteArrayInputStream(png));
        TextureSet.Texture texture = loader.loadBlockTextures().layer(BlockRegistry.STONE_TEXTURE);
        assertEquals(10, texture.rgbaAt(0, 0, 0));
        assertEquals(30, texture.rgbaAt(0, 0, 1));
        assertEquals(50, texture.rgbaAt(0, 0, 2));
        assertEquals(255, texture.rgbaAt(0, 0, 3));
        assertEquals(25, texture.rgbaAt(0, 15, 0));
    }

    @Test
    void shippedGrassSideIsOpaqueAndOtherCutoutsStayTransparent() {
        TextureSet textures = new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures();
        TextureSet.Texture grassSide = textures.layer(BlockRegistry.GRASS_SIDE_TEXTURE);
        TextureSet.Texture dirt = textures.layer(BlockRegistry.DIRT_TEXTURE);
        assertEquals(255, grassSide.rgbaAt(0, 0, 3));
        for (int channel = 0; channel < 3; channel++)
            assertEquals(dirt.rgbaAt(0, 0, channel), grassSide.rgbaAt(0, 0, channel));
        assertEquals(255, grassSide.rgbaAt(0, 15, 3));
        var overlay = textures.layer(BlockRegistry.DEFAULT.textureLayer("refi_grass_side_overlay"));
        int covered = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (overlay.rgbaAt(x, y, 3) > 128) covered++;
        assertTrue(covered > 0 && covered < 256);
        for (int y = 0; y < grassSide.height(); y++)
            for (int x = 0; x < grassSide.width(); x++) assertEquals(255, grassSide.rgbaAt(x, y, 3));
        assertTrue(java.util.stream.IntStream.range(0, 256)
                .anyMatch(i -> textures.layer(BlockRegistry.LEAVES_TEXTURE).rgbaAt(i % 16, i / 16, 3) == 0));
        assertEquals(0, textures.layer(BlockRegistry.GLASS_TEXTURE).rgbaAt(1, 1, 3));
        assertEquals(255, textures.layer(BlockRegistry.GLASS_TEXTURE).rgbaAt(0, 0, 3));
    }

    private static void assertCheckerboard(TextureSet.Texture texture) {
        assertEquals(255, texture.rgbaAt(0, 0, 0));
        assertEquals(0, texture.rgbaAt(0, 0, 1));
        assertEquals(255, texture.rgbaAt(0, 0, 2));
        assertEquals(255, texture.rgbaAt(0, 0, 3));
        assertEquals(0, texture.rgbaAt(4, 0, 0));
        assertEquals(0, texture.rgbaAt(4, 0, 2));
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, 0xff0a1e32 + y * 0x010000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
