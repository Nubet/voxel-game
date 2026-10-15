package com.norbertfila.lod;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Immutable authoritative changes inside a square of block columns, grouped by column. Within a column the changes
 * are ordered from the highest block down. An entry packs the world height in the upper 32 bits and the block ID in
 * the lowest 8 bits.
 */
public final class AreaEdits {
    public static final int MAX_SIZE = 1024;

    private final int minX;
    private final int minZ;
    private final int size;
    private final long revision;
    // Local column indices z * size + x, ascending, and the entries of each column.
    private final int[] columns;
    private final long[][] entries;

    private AreaEdits(int minX, int minZ, int size, long revision, int[] columns, long[][] entries) {
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.revision = revision;
        this.columns = columns;
        this.entries = entries;
    }

    public static AreaEdits none(int minX, int minZ, int size) {
        return new AreaEdits(minX, minZ, size, 0, new int[0], new long[0][]);
    }

    static long entry(int y, short packedChange) {
        return (long) y << 32 | packedChange & 0xff;
    }

    static AreaEdits of(int minX, int minZ, int size, long revision, Map<Integer, List<Long>> byColumn) {
        int[] columns =
                byColumn.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        long[][] entries = new long[columns.length][];
        for (int index = 0; index < columns.length; index++) {
            long[] column = byColumn.get(columns[index]).stream()
                    .mapToLong(Long::longValue)
                    .toArray();
            Arrays.sort(column);
            for (int low = 0, high = column.length - 1; low < high; low++, high--) {
                long swap = column[low];
                column[low] = column[high];
                column[high] = swap;
            }
            entries[index] = column;
        }
        return new AreaEdits(minX, minZ, size, revision, columns, entries);
    }

    public boolean covers(int minX, int minZ, int size) {
        return this.minX == minX && this.minZ == minZ && this.size == size;
    }

    public long revision() {
        return revision;
    }

    public int columnCount() {
        return columns.length;
    }

    /** World x of the {@code column}-th edited column. */
    public int columnX(int column) {
        return minX + columns[column] % size;
    }

    public int columnZ(int column) {
        return minZ + columns[column] / size;
    }

    /** Position of the column at world (x, z) among the edited columns, or -1 when it has no change. */
    public int find(int worldX, int worldZ) {
        int x = worldX - minX, z = worldZ - minZ;
        if (x < 0 || z < 0 || x >= size || z >= size) return -1;
        int found = Arrays.binarySearch(columns, z * size + x);
        return found < 0 ? -1 : found;
    }

    public int changeCount(int column) {
        return entries[column].length;
    }

    /** Height of the {@code change}-th change of a column; heights strictly decrease with {@code change}. */
    public int changeY(int column, int change) {
        return (int) (entries[column][change] >> 32);
    }

    public byte changeBlock(int column, int change) {
        return (byte) entries[column][change];
    }
}
