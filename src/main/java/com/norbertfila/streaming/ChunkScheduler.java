package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Set<TaskKey> cancelled = ConcurrentHashMap.newKeySet();
    private final Set<TaskKey> running = ConcurrentHashMap.newKeySet();

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
        cancelled.remove(TaskKey.of(task));
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

    public boolean cancel(ChunkPos position, long revision, long token) {
        TaskKey key = new TaskKey(Objects.requireNonNull(position), revision, token);
        cancelled.add(key);
        boolean removed = false;
        for (Runnable queued : executor.getQueue().toArray(Runnable[]::new)) {
            PrioritizedTask task = (PrioritizedTask) queued;
            if (TaskKey.of(task.task).equals(key) && executor.getQueue().remove(task)) {
                outstanding.release();
                cancelled.remove(key);
                removed = true;
            }
        }
        if (!removed && !running.contains(key)) cancelled.remove(key);
        return true;
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
                        task.position(),
                        task.revision(),
                        task.token(),
                        task.work().call(),
                        null);
            } catch (Throwable failure) {
                outcome = new ChunkTask.Outcome(task.position(), task.revision(), task.token(), null, failure);
            }
            if (cancelled.contains(TaskKey.of(task))) {
                outcome.discard();
                return;
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
            TaskKey key = TaskKey.of(task);
            running.add(key);
            try {
                execute(task);
            } finally {
                running.remove(key);
                cancelled.remove(key);
            }
        }

        private int compareTo(PrioritizedTask other) {
            double left = task.priority().score() - order * AGE_WEIGHT;
            double right = other.task.priority().score() - other.order * AGE_WEIGHT;
            int comparison = Double.compare(left, right);
            return comparison != 0 ? comparison : Long.compare(order, other.order);
        }
    }

    private record TaskKey(ChunkPos position, long revision, long token) {
        private static TaskKey of(ChunkTask task) {
            return new TaskKey(task.position(), task.revision(), task.token());
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
