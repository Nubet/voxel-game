package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotWriterTest {
    @TempDir
    Path directory;

    @Test
    void preservesPixelsAndUsesUniqueTimestampNamesWithoutOverwriting() throws Exception {
        Path screenshots = directory.resolve("screenshots");
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff0000);
        image.setRGB(1, 0, 0x00ff00);
        image.setRGB(0, 1, 0x0000ff);
        image.setRGB(1, 1, 0xffffff);
        var time = LocalDateTime.of(2026, 10, 19, 12, 30, 45);
        try (var writer = new ScreenshotWriter(screenshots)) {
            Path first = writer.write(image, time);
            byte[] original = Files.readAllBytes(first);
            assertEquals("2026-10-19_12.30.45.png", first.getFileName().toString());
            assertEquals(
                    "2026-10-19_12.30.45_2.png",
                    writer.write(image, time).getFileName().toString());
            assertEquals(
                    "2026-10-19_12.30.45_3.png",
                    writer.write(image, time).getFileName().toString());
            assertArrayEquals(original, Files.readAllBytes(first));
            BufferedImage decoded = ImageIO.read(first.toFile());
            assertEquals(2, decoded.getWidth());
            assertEquals(2, decoded.getHeight());
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 2; x++) assertEquals(image.getRGB(x, y), decoded.getRGB(x, y));
        }
    }

    @Test
    void closeFinishesAcceptedWritesAndRejectsNewRequests() throws Exception {
        var writer = new ScreenshotWriter(directory);
        var saved = writer.save(new BufferedImage(3, 1, BufferedImage.TYPE_INT_RGB));
        writer.close();
        assertTrue(Files.isRegularFile(saved.get(5, TimeUnit.SECONDS)));
        var rejected = writer.save(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        assertInstanceOf(
                IOException.class,
                assertThrows(ExecutionException.class, rejected::get).getCause());
    }

    @Test
    void reportsDirectoryFailureThroughFuture() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "existing file");
        try (var writer = new ScreenshotWriter(blocked)) {
            var result = writer.save(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            var failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            assertEquals("existing file", Files.readString(blocked));
        }
    }
}
