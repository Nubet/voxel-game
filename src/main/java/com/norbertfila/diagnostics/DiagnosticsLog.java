package com.norbertfila.diagnostics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class DiagnosticsLog implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger("voxel");
    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final FileHandler fileHandler;
    private final Thread.UncaughtExceptionHandler previousHandler;

    private DiagnosticsLog(FileHandler fileHandler, Thread.UncaughtExceptionHandler previousHandler) {
        this.fileHandler = fileHandler;
        this.previousHandler = previousHandler;
    }

    public static DiagnosticsLog install() {
        try {
            Path directory = Path.of("logs");
            Files.createDirectories(directory);
            FileHandler file = new FileHandler(
                    directory
                            .resolve("voxel-" + LocalDateTime.now().format(START_TIME) + "-%g.log")
                            .toString(),
                    4 * 1024 * 1024,
                    4,
                    true);
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.ALL);
            LogManager.getLogManager().reset();
            LOG.setLevel(Level.ALL);
            LOG.addHandler(file);
            ConsoleHandler console = new ConsoleHandler();
            console.setLevel(Level.INFO);
            LOG.addHandler(console);
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
                LOG.log(
                        Level.SEVERE,
                        "Uncaught exception thread=" + thread.getName() + " context=" + RuntimeContext.value(),
                        failure);
                if (previous != null) previous.uncaughtException(thread, failure);
            });
            LOG.info(() -> "Diagnostics started java=" + Runtime.version() + " os=" + System.getProperty("os.name")
                    + " arch=" + System.getProperty("os.arch") + " cwd="
                    + Path.of("").toAbsolutePath());
            return new DiagnosticsLog(file, previous);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot initialize diagnostics log", failure);
        }
    }

    public static Logger logger(Class<?> source) {
        return Logger.getLogger("voxel." + Objects.requireNonNull(source).getName());
    }

    public static void context(String value) {
        RuntimeContext.set(value);
    }

    @Override
    public void close() {
        LOG.info("Diagnostics stopped");
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        fileHandler.close();
    }
}
