package com.norbertfila.streaming;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkScheduler implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final BlockingQueue<ChunkTask.Outcome> ready;
    private final Semaphore outstanding;
    private final AtomicLong sequence = new AtomicLong();

    public ChunkScheduler(int workerCount, int taskQueueCapacity, int readyQueueCapacity) {
        if (workerCount <= 0 || taskQueueCapacity <= 0 || readyQueueCapacity <= 0) {
            throw new IllegalArgumentException("Scheduler capacities must be positive");
        }
        PriorityBlockingQueue<Runnable> tasks = new PriorityBlockingQueue<>(
                taskQueueCapacity, (left, right) -> ((PrioritizedTask) left).compareTo((PrioritizedTask) right));
        executor = new ThreadPoolExecutor(
                workerCount, workerCount, 0L, TimeUnit.MILLISECONDS, tasks, new ThreadPoolExecutor.AbortPolicy());
        ready = new ArrayBlockingQueue<>(readyQueueCapacity);
        outstanding = new Semaphore(workerCount + taskQueueCapacity);
    }

    public boolean submit(ChunkTask task) {
        Objects.requireNonNull(task);
        if (!outstanding.tryAcquire()) return false;
        try {
            executor.execute(new PrioritizedTask(task, sequence.getAndIncrement()));
            return true;
        } catch (RejectedExecutionException rejected) {
            outstanding.release();
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
        try {
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
        } finally {
            outstanding.release();
        }
    }

    private final class PrioritizedTask implements Runnable {
        private static final double AGE_WEIGHT = 0.001;
        private final ChunkTask task;
        private final long order;

        private PrioritizedTask(ChunkTask task, long order) {
            this.task = task;
            this.order = order;
        }

        @Override
        public void run() {
            execute(task);
        }

        private int compareTo(PrioritizedTask other) {
            double left = task.priority().score() - order * AGE_WEIGHT;
            double right = other.task.priority().score() - other.order * AGE_WEIGHT;
            int comparison = Double.compare(left, right);
            return comparison != 0 ? comparison : Long.compare(order, other.order);
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
