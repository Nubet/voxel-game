package com.norbertfila.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopFilesTest {
    @TempDir
    Path directory;

    @Test
    void missingFileFailsAsynchronouslyWithoutLaunchingAnApplication() {
        var result = DesktopFiles.open(directory.resolve("missing.png"));
        var failure = assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        assertInstanceOf(UncheckedIOException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("missing.png"));
    }
}
