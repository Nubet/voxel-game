package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkScheduler<T> implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final BlockingQueue<ChunkTask.Outcome<T>> ready;
    private final Semaphore outstanding;
    private final int outstandingLimit;
    private final AtomicLong sequence = new AtomicLong();
    private final Set<TaskKey> cancelled = ConcurrentHashMap.newKeySet();
    private final Set<TaskKey> submitted = ConcurrentHashMap.newKeySet();
    private final Set<TaskKey> running = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<ChunkPos, TaskKey> latest = new ConcurrentHashMap<>();

    public ChunkScheduler(int workerCount, int taskQueueCapacity, int readyQueueCapacity) {
        if (workerCount <= 0 || taskQueueCapacity <= 0 || readyQueueCapacity <= 0) {
            throw new IllegalArgumentException("Scheduler capacities must be positive");
        }
        PriorityBlockingQueue<Runnable> tasks = new PriorityBlockingQueue<>(
                taskQueueCapacity, (left, right) -> ((PrioritizedTask) left).compareTo((PrioritizedTask) right));
        executor = new ThreadPoolExecutor(
                workerCount, workerCount, 0L, TimeUnit.MILLISECONDS, tasks, new ThreadPoolExecutor.AbortPolicy());
        ready = new ArrayBlockingQueue<>(readyQueueCapacity);
        outstandingLimit = workerCount + taskQueueCapacity;
        outstanding = new Semaphore(outstandingLimit);
    }

    public boolean submit(ChunkTask<T> task) {
        Objects.requireNonNull(task);
        cancelled.remove(TaskKey.of(task));
        if (!outstanding.tryAcquire()) return false;
        TaskKey key = TaskKey.of(task);
        submitted.add(key);
        try {
            executor.execute(new PrioritizedTask(task, sequence.getAndIncrement()));
            return true;
        } catch (RejectedExecutionException rejected) {
            submitted.remove(key);
            outstanding.release();
            return false;
        }
    }

    public boolean submitLatest(ChunkTask<T> task) {
        Objects.requireNonNull(task);
        TaskKey key = TaskKey.of(task);
        TaskKey previous = latest.put(task.position(), key);
        if (previous != null && !previous.equals(key))
            cancel(previous.position(), previous.revision(), previous.token());
        if (submit(task)) return true;
        latest.remove(task.position(), key);
        return false;
    }

    public ChunkTask.Outcome<T> pollReady() {
        return ready.poll();
    }

    public ChunkTask.Outcome<T> pollLatestReady() {
        ChunkTask.Outcome<T> outcome;
        while ((outcome = ready.poll()) != null) {
            if (isLatest(outcome)) return outcome;
            outcome.discard();
        }
        return null;
    }

    public boolean isLatest(ChunkTask.Outcome<T> outcome) {
        Objects.requireNonNull(outcome);
        return new TaskKey(outcome.position(), outcome.revision(), outcome.token())
                .equals(latest.get(outcome.position()));
    }

    public List<ChunkTask.Outcome<T>> drainReady() {
        List<ChunkTask.Outcome<T>> outcomes = new ArrayList<>();
        ready.drainTo(outcomes);
        return outcomes;
    }

    public int queuedTasks() {
        return executor.getQueue().size();
    }

    public int activeTasks() {
        return executor.getActiveCount();
    }

    public int outstandingJobs() {
        return outstandingLimit - outstanding.availablePermits();
    }

    public int cancelOutside(ChunkPos center, int radius) {
        return cancelOutside(center, radius, false);
    }

    public int cancelOutside(ChunkPos center, int radius, boolean square) {
        if (radius < 0) throw new IllegalArgumentException("Radius must not be negative");
        Set<TaskKey> candidates = new HashSet<>();
        for (Runnable queued : executor.getQueue()) candidates.add(TaskKey.of(((PrioritizedTask) queued).task));
        candidates.addAll(submitted);
        int cancelledCount = 0;
        for (TaskKey key : candidates) {
            if (!withinRadius(key.position(), center, radius, square)) {
                cancel(key.position(), key.revision(), key.token());
                cancelledCount++;
            }
        }
        return cancelledCount;
    }

    public int discardReadyOutside(ChunkPos center, int radius) {
        return discardReadyOutside(center, radius, false);
    }

    public int discardReadyOutside(ChunkPos center, int radius, boolean square) {
        if (radius < 0) throw new IllegalArgumentException("Radius must not be negative");
        List<ChunkTask.Outcome<T>> keep = new ArrayList<>();
        int discarded = 0;
        for (ChunkTask.Outcome<T> outcome : drainReady()) {
            if (withinRadius(outcome.position(), center, radius, square)) keep.add(outcome);
            else {
                outcome.discard();
                discarded++;
            }
        }
        ready.addAll(keep);
        return discarded;
    }

    public boolean cancel(ChunkPos position, long revision, long token) {
        TaskKey key = new TaskKey(Objects.requireNonNull(position), revision, token);
        cancelled.add(key);
        boolean removed = false;
        for (Runnable queued : executor.getQueue().toArray(Runnable[]::new)) {
            PrioritizedTask task = (PrioritizedTask) queued;
            if (TaskKey.of(task.task).equals(key) && executor.getQueue().remove(task)) {
                outstanding.release();
                submitted.remove(key);
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

    private void execute(ChunkTask<T> task) {
        try {
            ChunkTask.Outcome<T> outcome;
            try {
                outcome = new ChunkTask.Outcome<T>(
                        task.position(),
                        task.revision(),
                        task.token(),
                        task.work().call(),
                        null);
            } catch (Throwable failure) {
                outcome = new ChunkTask.Outcome<T>(task.position(), task.revision(), task.token(), null, failure);
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
            submitted.remove(TaskKey.of(task));
        }
    }

    private static boolean withinRadius(ChunkPos position, ChunkPos center, int radius) {
        return withinRadius(position, center, radius, false);
    }

    private static boolean withinRadius(ChunkPos position, ChunkPos center, int radius, boolean square) {
        long dx = position.x() - (long) center.x();
        long dz = position.z() - (long) center.z();
        return square ? Math.abs(dx) <= radius && Math.abs(dz) <= radius : dx * dx + dz * dz <= (long) radius * radius;
    }

    private final class PrioritizedTask implements Runnable {
        private static final double AGE_WEIGHT = 0.001;
        private final ChunkTask<T> task;
        private final long order;

        private PrioritizedTask(ChunkTask<T> task, long order) {
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
        private static TaskKey of(ChunkTask<?> task) {
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
        latest.clear();
    }
}
