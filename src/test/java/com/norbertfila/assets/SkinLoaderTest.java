package com.norbertfila.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkinLoaderTest {
    @TempDir
    Path directory;

    @Test
    void missingInvalidAndCorruptExternalSkinsUseCachedBundledFallback() throws IOException {
        var path = directory.resolve("player.png");
        var loader = new SkinLoader();
        var missing = loader.load(path);
        assertEquals(SkinLoader.BUNDLED_PATH, missing.source());
        assertTrue(missing.diagnostic().contains(path.toString()));
        assertArrayEquals(SkinLoader.loadDefault().rgba(), missing.skin().rgba());
        for (int[] dimensions : new int[][] {{64, 32}, {32, 64}, {128, 128}}) {
            ImageIO.write(
                    new BufferedImage(dimensions[0], dimensions[1], BufferedImage.TYPE_INT_ARGB), "png", path.toFile());
            var invalid = loader.load(path);
            assertSame(missing.skin(), invalid.skin());
            assertTrue(invalid.diagnostic().contains("64x64"));
        }
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", path.toFile());
        byte[] png = Files.readAllBytes(path);
        Files.write(path, java.util.Arrays.copyOf(png, 40));
        var corrupt = loader.load(path);
        assertSame(missing.skin(), corrupt.skin());
        assertFalse(corrupt.diagnostic().isEmpty());
        Files.write(path, new byte[] {1, 2, 3});
        assertSame(missing.skin(), loader.load(path).skin());
    }

    @Test
    void externalReloadReadsChangedPixelsAndPreservesAlpha() throws IOException {
        var path = directory.resolve("player.png");
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var loader = new SkinLoader();
        image.setRGB(0, 0, 0x7f123456);
        ImageIO.write(image, "png", path.toFile());
        var first = loader.load(path);
        assertEquals(path.toString(), first.source());
        assertEquals("", first.diagnostic());
        assertEquals(0x7f, Byte.toUnsignedInt(first.skin().rgba()[3]));
        image.setRGB(0, 0, 0xffabcdef);
        ImageIO.write(image, "png", path.toFile());
        assertEquals(0xab, Byte.toUnsignedInt(loader.load(path).skin().rgba()[0]));
    }

    @Test
    void emptyOversizedAndNonPngInputsAreRejected() {
        assertThrows(IOException.class, () -> SkinLoader.read(new ByteArrayInputStream(new byte[0])));
        assertThrows(IOException.class, () -> SkinLoader.read(new ByteArrayInputStream(new byte[1024 * 1024 + 1])));
        assertThrows(
                IOException.class,
                () -> SkinLoader.read(
                        new ByteArrayInputStream("not a png image".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
