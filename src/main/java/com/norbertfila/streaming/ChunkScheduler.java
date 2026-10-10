package com.norbertfila.streaming;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ChunkScheduler implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final BlockingQueue<ChunkTask.Outcome> ready;

    public ChunkScheduler(int workerCount, int taskQueueCapacity, int readyQueueCapacity) {
        if (workerCount <= 0 || taskQueueCapacity <= 0 || readyQueueCapacity <= 0) {
            throw new IllegalArgumentException("Scheduler capacities must be positive");
        }
        executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(taskQueueCapacity),
                new ThreadPoolExecutor.AbortPolicy());
        ready = new ArrayBlockingQueue<>(readyQueueCapacity);
    }

    public boolean submit(ChunkTask task) {
        Objects.requireNonNull(task);
        try {
            executor.execute(() -> execute(task));
            return true;
        } catch (RejectedExecutionException rejected) {
            return false;
        }
    }

    public ChunkTask.Outcome pollReady() {
        return ready.poll();
    }

    public List<ChunkTask.Outcome> drainReady() {
        List<ChunkTask.Outcome> outcomes = new ArrayList<>();
        ready.drainTo(outcomes);
        return outcomes;
    }

    public int queuedTasks() {
        return executor.getQueue().size();
    }

    public int activeTasks() {
        return executor.getActiveCount();
    }

    public void shutdown() {
        executor.shutdown();
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout);
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        return executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public void shutdownNow() {
        executor.shutdownNow();
    }

    private void execute(ChunkTask task) {
        ChunkTask.Outcome outcome;
        try {
            outcome = new ChunkTask.Outcome(
                    task.position(), task.token(), task.work().call(), null);
        } catch (Throwable failure) {
            outcome = new ChunkTask.Outcome(task.position(), task.token(), null, failure);
        }
        try {
            ready.put(outcome);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(Duration.ofSeconds(5))) shutdownNow();
        } catch (InterruptedException interrupted) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
