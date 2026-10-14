package com.norbertfila.streaming;

import com.norbertfila.world.ChunkPos;
import java.util.Objects;

/**
 * Scheduling urgency of a chunk task. Categories are strict and ordered as declared, so collision work always runs
 * before edits, edits before missing geometry, and missing geometry before refreshing visible geometry. Within a category, nearer chunks run first, but every later submission adds {@link
 * #AGE_STEP} to its rank, so a queued far chunk eventually ranks ahead of new near chunks and cannot starve.
 */
public record ChunkPriority(Category category, long distanceSquared) {
    /** Rank added per submission; a chunk 16 sections away waits for at most 256 newer, nearer submissions. */
    static final long AGE_STEP = 1;

    public enum Category {
        /** Needed by player collision or session activation. */
        COLLISION,
        /** Remesh caused by a block edit. */
        EDIT,
        /** Generation or first mesh of a chunk that has no geometry on screen yet. */
        VISUAL,
        /** Remesh of a chunk whose current geometry stays visible until the new mesh arrives. */
        REFRESH
    }

    public ChunkPriority {
        Objects.requireNonNull(category);
        if (distanceSquared < 0) throw new IllegalArgumentException("Invalid chunk priority");
    }

    public static ChunkPriority collision() {
        return new ChunkPriority(Category.COLLISION, 0);
    }

    public static ChunkPriority edit(ChunkPos position, ChunkPos center) {
        return new ChunkPriority(Category.EDIT, distanceSquared(position, center));
    }

    public static ChunkPriority visual(ChunkPos position, ChunkPos center) {
        return new ChunkPriority(Category.VISUAL, distanceSquared(position, center));
    }

    public static ChunkPriority refresh(ChunkPos position, ChunkPos center) {
        return new ChunkPriority(Category.REFRESH, distanceSquared(position, center));
    }

    static ChunkPriority unprioritized() {
        return new ChunkPriority(Category.VISUAL, Long.MAX_VALUE);
    }

    boolean moreUrgentThan(ChunkPriority other) {
        int category = this.category.compareTo(other.category);
        return category != 0 ? category < 0 : distanceSquared < other.distanceSquared;
    }

    /** Orders tasks by category, then by distance aged by submission order, then by submission order. */
    static int compare(ChunkPriority left, long leftOrder, ChunkPriority right, long rightOrder) {
        int category = left.category.compareTo(right.category);
        if (category != 0) return category;
        int rank = Long.compare(left.rank(leftOrder), right.rank(rightOrder));
        return rank != 0 ? rank : Long.compare(leftOrder, rightOrder);
    }

    private long rank(long order) {
        long age = order > Long.MAX_VALUE / AGE_STEP ? Long.MAX_VALUE : order * AGE_STEP;
        return distanceSquared > Long.MAX_VALUE - age ? Long.MAX_VALUE : distanceSquared + age;
    }

    /** Squared 3D distance in chunk units; vertical distance counts so sections near the player's height go first. */
    static long distanceSquared(ChunkPos position, ChunkPos center) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(center);
        long dx = (long) position.x() - center.x();
        long dy = (long) position.y() - center.y();
        long dz = (long) position.z() - center.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
