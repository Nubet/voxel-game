package com.norbertfila.persistence.world;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public final class WorldCatalogBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Files.isDirectory(Path.of(args[0])))
            throw new IllegalArgumentException("Expected an existing temporary output directory");
        Path root = Files.createTempDirectory(Path.of(args[0]), "world02-");
        var catalog = new WorldCatalog(root, "default", 11);
        System.out.println("scene=initial snapshots seed=0 generator=default/11 resolution=NA GPU=NA Java="
                + Runtime.version() + " OS=" + System.getProperty("os.name") + " CPU="
                + System.getenv("PROCESSOR_IDENTIFIER") + " filesystem="
                + Files.getFileStore(root).type());
        int created = 0;
        for (int count : new int[] {0, 100, 1000}) {
            long started = System.nanoTime();
            while (created < count) {
                int id = ++created;
                catalog.create(new WorldMetadata(
                        new WorldIdentity(new UUID(0, id), 0, "default", 11), "Świat " + id, Instant.ofEpochMilli(id)));
            }
            double creationMillis = (System.nanoTime() - started) / 1e6;
            for (int warmup = 0; warmup < 3; warmup++) catalog.list();
            long[] samples = new long[11];
            for (int sample = 0; sample < samples.length; sample++) {
                started = System.nanoTime();
                var entries = catalog.list();
                samples[sample] = System.nanoTime() - started;
                check(entries.size() == count, "Incorrect list size");
                check(entries.stream().allMatch(e -> e.status() == WorldEntry.Status.READY), "Invalid catalog entry");
                if (count > 0) check(entries.getFirst().id().equals(new UUID(0, count)), "Incorrect ordering");
            }
            Arrays.sort(samples);
            System.out.printf(
                    java.util.Locale.ROOT,
                    "PASS entries=%d creationIncrementMs=%.3f warmListMedianMs=%.3f warmListMaxMs=%.3f%n",
                    count,
                    creationMillis,
                    samples[5] / 1e6,
                    samples[10] / 1e6);
        }
        System.out.println("WORLD-02 catalog benchmark PASS output=" + root);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
