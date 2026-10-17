package com.norbertfila.diagnostics;

/** Cumulative, bounded timings. Percentiles are bucket upper bounds with at most 6.25% rounding. */
public final class DurationHistogram {
    private final long[] counts = new long[1024];
    private Snapshot cached;

    public synchronized void record(long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("Negative duration");
        int exponent = nanos == 0 ? 0 : 63 - Long.numberOfLeadingZeros(nanos);
        int fraction = exponent < 4 ? (int) nanos : (int) (nanos >>> (exponent - 4)) - 16;
        counts[exponent < 4 ? fraction : exponent * 16 + fraction]++;
        cached = null;
    }

    public synchronized Snapshot snapshot() {
        if (cached == null) cached = new Snapshot(counts);
        return cached;
    }

    public static final class Snapshot {
        private final long[] counts;

        private Snapshot(long[] counts) {
            this.counts = counts.clone();
        }

        public double percentileMillisSince(Snapshot before, double quantile) {
            if (!(quantile > 0 && quantile <= 1)) throw new IllegalArgumentException("Invalid quantile");
            long total = 0;
            for (int i = 0; i < counts.length; i++) total += counts[i] - before.counts[i];
            if (total == 0) return 0;
            long target = (long) Math.ceil(total * quantile), cumulative = 0;
            for (int i = 0; i < counts.length; i++) {
                cumulative += counts[i] - before.counts[i];
                if (cumulative >= target) {
                    int exponent = i / 16;
                    double upper = i < 16 ? i : Math.scalb(17.0 + i % 16, exponent - 4) - 1;
                    return upper / 1e6;
                }
            }
            throw new IllegalStateException("Histogram counters went backwards");
        }
    }
}
