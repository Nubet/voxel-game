package com.norbertfila.persistence;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public final class ScreenshotWriter implements AutoCloseable {
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private final Path directory;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1),
            Thread.ofPlatform().name("screenshot-writer").factory());

    public ScreenshotWriter(Path directory) {
        this.directory = directory;
    }

    public CompletableFuture<Path> save(BufferedImage image) {
        var result = new CompletableFuture<Path>();
        LocalDateTime capturedAt = LocalDateTime.now();
        try {
            worker.execute(() -> {
                try {
                    result.complete(write(image, capturedAt));
                } catch (IOException | RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            result.completeExceptionally(new IOException("Screenshot queue is full or closing", failure));
        }
        return result;
    }

    Path write(BufferedImage image, LocalDateTime capturedAt) throws IOException {
        Files.createDirectories(directory);
        String name = NAME.format(capturedAt);
        for (int suffix = 1; ; suffix++) {
            Path file = directory.resolve(name + (suffix == 1 ? "" : "_" + suffix) + ".png");
            OutputStream output;
            try {
                output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
            try (output) {
                if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder is unavailable");
            } catch (IOException | RuntimeException failure) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            return file;
        }
    }

    @Override
    public void close() {
        worker.close();
    }
}
