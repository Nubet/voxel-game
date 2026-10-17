package com.norbertfila.streaming;

import com.norbertfila.diagnostics.DurationHistogram;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChunkScheduler<T> implements AutoCloseable {
    private final DurationHistogram execution = new DurationHistogram(),
            wait = new DurationHistogram(),
            intake = new DurationHistogram();

    public record Timings(
            DurationHistogram.Snapshot execution,
            DurationHistogram.Snapshot queueWait,
            DurationHistogram.Snapshot intake) {}

    public Timings timings() {
        return new Timings(execution.snapshot(), wait.snapshot(), intake.snapshot());
    }

    private final java.util.concurrent.atomic.LongAdder workNanos = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder completedJobs = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder queueWaitNanos = new java.util.concurrent.atomic.LongAdder();

    public long workNanos() {
        return workNanos.sum();
    }

    public long completedJobs() {
        return completedJobs.sum();
    }

    /** Total time started tasks spent queued before a worker picked them up. */
    public long queueWaitNanos() {
        return queueWaitNanos.sum();
    }

    private static final Logger LOG = Logger.getLogger(ChunkScheduler.class.getName());
    private final ThreadPoolExecutor executor;
    private final BlockingQueue<ChunkTask.Outcome<T>> ready;
    private final Semaphore outstanding;
    private final int outstandingLimit;
    private final AtomicLong sequence = new AtomicLong();
    private final Set<TaskKey> cancelled = ConcurrentHashMap.newKeySet();
    private final Set<TaskKey> submitted = ConcurrentHashMap.newKeySet();
    private final Set<TaskKey> running = ConcurrentHashMap.newKeySet();
    // A position stays here only while its newest task is queued, running or holds an unpolled outcome, so the map is
    // bounded by in-flight work. Removal is conditional because an older task may finish after a newer submission.
    private final ConcurrentMap<ChunkPos, TaskKey> latest = new ConcurrentHashMap<>();
    private final Object readyLock = new Object();
    private volatile boolean closed;

    public ChunkScheduler(int workerCount, int taskQueueCapacity, int readyQueueCapacity) {
        if (workerCount <= 0 || taskQueueCapacity <= 0 || readyQueueCapacity <= 0) {
            throw new IllegalArgumentException("Scheduler capacities must be positive");
        }
        PriorityBlockingQueue<Runnable> tasks = new PriorityBlockingQueue<>(
                taskQueueCapacity, (left, right) -> ((PrioritizedTask) left).compareTo((PrioritizedTask) right));
        executor = new ThreadPoolExecutor(
                workerCount, workerCount, 0L, TimeUnit.MILLISECONDS, tasks, new ThreadPoolExecutor.AbortPolicy());
        // promote() re-inserts tasks straight into the queue, which only running workers will drain.
        executor.prestartAllCoreThreads();
        ready = new ArrayBlockingQueue<>(readyQueueCapacity);
        // Reserve one result slot per accepted job so workers never lose an outcome to a full queue
        outstandingLimit = Math.min(workerCount + taskQueueCapacity, readyQueueCapacity);
        outstanding = new Semaphore(outstandingLimit);
    }

    public boolean submit(ChunkTask<T> task) {
        Objects.requireNonNull(task);
        if (closed) return false;
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
        synchronized (readyLock) {
            ChunkTask.Outcome<T> outcome = ready.poll();
            if (outcome != null) {
                intake.record(System.nanoTime() - outcome.completedAt());
                outstanding.release();
                forget(TaskKey.of(outcome));
            }
            return outcome;
        }
    }

    public List<ChunkTask.Outcome<T>> drainReady() {
        List<ChunkTask.Outcome<T>> outcomes = new ArrayList<>();
        synchronized (readyLock) {
            ready.drainTo(outcomes);
            outstanding.release(outcomes.size());
        }
        for (ChunkTask.Outcome<T> outcome : outcomes) forget(TaskKey.of(outcome));
        return outcomes;
    }

    /** Bookkeeping entries held for queued, running, cancelled and unpolled work. */
    public int metadataEntries() {
        return latest.size() + submitted.size() + cancelled.size() + running.size();
    }

    /**
     * Raises the priority of a still-queued task for {@code position}. Running or finished tasks are unaffected.
     * Returns true when a queued task was re-prioritized.
     */
    public boolean promote(ChunkPos position, ChunkPriority priority) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(priority);
        BlockingQueue<Runnable> queue = executor.getQueue();
        for (Runnable queued : queue.toArray(Runnable[]::new)) {
            PrioritizedTask task = (PrioritizedTask) queued;
            if (!task.task.position().equals(position) || !priority.moreUrgentThan(task.task.priority())) continue;
            if (!queue.remove(task)) return false;
            queue.add(task.withPriority(priority));
            return true;
        }
        return false;
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

    public boolean hasCapacity() {
        return outstanding.availablePermits() > 0;
    }

    public int cancelOutside(ChunkPos center, int radius) {
        return cancelOutside(center, radius, false);
    }

    public int cancelOutside(ChunkPos center, int radius, boolean square) {
        return cancelOutside(center, radius, square, Set.of());
    }

    public int cancelOutside(ChunkPos center, int radius, boolean square, Set<ChunkPos> protectedPositions) {
        Objects.requireNonNull(protectedPositions);
        if (radius < 0) throw new IllegalArgumentException("Radius must not be negative");
        Set<TaskKey> candidates = new HashSet<>();
        for (Runnable queued : executor.getQueue()) candidates.add(TaskKey.of(((PrioritizedTask) queued).task));
        candidates.addAll(submitted);
        int cancelledCount = 0;
        for (TaskKey key : candidates) {
            if (!protectedPositions.contains(key.position()) && !withinRadius(key.position(), center, radius, square)) {
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
        return discardReadyOutside(center, radius, square, Set.of());
    }

    public int discardReadyOutside(ChunkPos center, int radius, boolean square, Set<ChunkPos> protectedPositions) {
        Objects.requireNonNull(protectedPositions);
        if (radius < 0) throw new IllegalArgumentException("Radius must not be negative");
        synchronized (readyLock) {
            List<ChunkTask.Outcome<T>> keep = new ArrayList<>();
            int discarded = 0;
            ready.drainTo(keep);
            List<ChunkTask.Outcome<T>> retained = new ArrayList<>(keep.size());
            for (ChunkTask.Outcome<T> outcome : keep) {
                if (protectedPositions.contains(outcome.position())
                        || withinRadius(outcome.position(), center, radius, square)) retained.add(outcome);
                else {
                    outcome.discard();
                    discarded++;
                    outstanding.release();
                    forget(TaskKey.of(outcome));
                }
            }
            ready.addAll(retained);
            return discarded;
        }
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
                forget(key);
                removed = true;
            }
        }
        if (!removed && !submitted.contains(key)) cancelled.remove(key);
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
        for (Runnable queued : executor.shutdownNow()) {
            TaskKey key = TaskKey.of(((PrioritizedTask) queued).task);
            submitted.remove(key);
            cancelled.remove(key);
            forget(key);
            outstanding.release();
        }
    }

    private void execute(ChunkTask<T> task) {
        long started = System.nanoTime();
        try {
            ChunkTask.Outcome<T> outcome;
            try {
                outcome = new ChunkTask.Outcome<T>(
                        task.position(),
                        task.revision(),
                        task.meshDependencyRevision(),
                        task.token(),
                        task.work().call(),
                        null);
            } catch (Throwable failure) {
                if (!closed)
                    LOG.log(
                            Level.SEVERE,
                            "Chunk task failed position=" + task.position() + " revision=" + task.revision() + " token="
                                    + task.token(),
                            failure);
                outcome = new ChunkTask.Outcome<T>(
                        task.position(), task.revision(), task.meshDependencyRevision(), task.token(), null, failure);
            }
            if (cancelled.contains(TaskKey.of(task))) {
                outcome.discard();
                outstanding.release();
                forget(TaskKey.of(task));
                return;
            }
            synchronized (readyLock) {
                if (closed) {
                    outcome.discard();
                    outstanding.release();
                    forget(TaskKey.of(task));
                    return;
                }
                if (!ready.offer(outcome)) {
                    throw new IllegalStateException("Reserved chunk result slot unavailable: " + outcome.position());
                }
            }
        } finally {
            long elapsed = System.nanoTime() - started;
            workNanos.add(elapsed);
            execution.record(elapsed);
            completedJobs.increment();
            submitted.remove(TaskKey.of(task));
        }
    }

    private void forget(TaskKey key) {
        latest.remove(key.position(), key);
    }

    private static boolean withinRadius(ChunkPos position, ChunkPos center, int radius, boolean square) {
        long dx = position.x() - (long) center.x();
        long dz = position.z() - (long) center.z();
        return square ? Math.abs(dx) <= radius && Math.abs(dz) <= radius : dx * dx + dz * dz <= (long) radius * radius;
    }

    private final class PrioritizedTask implements Runnable {
        private final ChunkTask<T> task;
        private final long order;
        private final long submittedAt;

        private PrioritizedTask(ChunkTask<T> task, long order) {
            this(task, order, System.nanoTime());
        }

        private PrioritizedTask(ChunkTask<T> task, long order, long submittedAt) {
            this.task = task;
            this.order = order;
            this.submittedAt = submittedAt;
        }

        private PrioritizedTask withPriority(ChunkPriority priority) {
            return new PrioritizedTask(
                    new ChunkTask<>(
                            task.position(),
                            task.revision(),
                            task.meshDependencyRevision(),
                            task.token(),
                            priority,
                            task.work()),
                    order,
                    submittedAt);
        }

        @Override
        public void run() {
            long waited = System.nanoTime() - submittedAt;
            queueWaitNanos.add(waited);
            wait.record(waited);
            TaskKey key = TaskKey.of(task);
            running.add(key);
            try {
                if (closed || cancelled.contains(key)) {
                    submitted.remove(key);
                    outstanding.release();
                    forget(key);
                    return;
                }
                execute(task);
            } finally {
                running.remove(key);
                cancelled.remove(key);
            }
        }

        private int compareTo(PrioritizedTask other) {
            return ChunkPriority.compare(task.priority(), order, other.task.priority(), other.order);
        }
    }

    private record TaskKey(ChunkPos position, long revision, long token) {
        private static TaskKey of(ChunkTask<?> task) {
            return new TaskKey(task.position(), task.revision(), task.token());
        }

        private static TaskKey of(ChunkTask.Outcome<?> outcome) {
            return new TaskKey(outcome.position(), outcome.revision(), outcome.token());
        }
    }

    @Override
    public void close() {
        synchronized (readyLock) {
            closed = true;
        }
        shutdownNow();
        try {
            if (!awaitTermination(Duration.ofSeconds(5)))
                LOG.warning("Chunk workers did not terminate within five seconds");
        } catch (InterruptedException interrupted) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (ChunkTask.Outcome<T> outcome : drainReady()) outcome.discard();
        latest.clear();
    }
}
