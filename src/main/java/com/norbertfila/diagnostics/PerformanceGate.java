package com.norbertfila.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PerformanceGate {
    public static final String BENCHMARK_PROPERTY = "voxel.benchmark";

    private PerformanceGate() {}

    public static boolean benchmarkEnabled() {
        return Boolean.getBoolean(BENCHMARK_PROPERTY);
    }

    public static Evaluation evaluate(Sample sample, Baseline baseline) {
        Objects.requireNonNull(sample);
        Objects.requireNonNull(baseline);
        boolean matchingHardware = baseline.hardwareProfile().equals(sample.hardwareProfile());
        boolean hardFail = baseline.hardFail() && matchingHardware;
        List<Violation> violations = new ArrayList<>();
        check(
                "generationMillis",
                sample.generationMillis(),
                baseline.generationMillis(),
                baseline,
                hardFail,
                violations);
        check("meshingMillis", sample.meshingMillis(), baseline.meshingMillis(), baseline, hardFail, violations);
        check("p99FrameMillis", sample.p99FrameMillis(), baseline.p99FrameMillis(), baseline, hardFail, violations);
        Status status = violations.stream().anyMatch(violation -> violation.severity() == Status.FAIL)
                ? Status.FAIL
                : violations.isEmpty() ? Status.PASS : Status.ALERT;
        return new Evaluation(status, matchingHardware, List.copyOf(violations));
    }

    private static void check(
            String metric,
            double actual,
            double expected,
            Baseline baseline,
            boolean hardFail,
            List<Violation> violations) {
        double alertThreshold = expected * baseline.alertFactor();
        if (actual <= alertThreshold) return;
        double failThreshold = expected * baseline.failFactor();
        violations.add(new Violation(
                metric,
                actual,
                hardFail && actual > failThreshold ? failThreshold : alertThreshold,
                hardFail && actual > failThreshold ? Status.FAIL : Status.ALERT));
    }

    public enum Status {
        PASS,
        ALERT,
        FAIL
    }

    public record Sample(String hardwareProfile, double generationMillis, double meshingMillis, double p99FrameMillis) {
        public Sample {
            Objects.requireNonNull(hardwareProfile);
            if (!finiteNonNegative(generationMillis)
                    || !finiteNonNegative(meshingMillis)
                    || !finiteNonNegative(p99FrameMillis))
                throw new IllegalArgumentException("Invalid performance sample");
        }
    }

    public record Baseline(
            String hardwareProfile,
            double generationMillis,
            double meshingMillis,
            double p99FrameMillis,
            double alertFactor,
            double failFactor,
            boolean hardFail) {
        public Baseline {
            Objects.requireNonNull(hardwareProfile);
            if (!finiteNonNegative(generationMillis)
                    || !finiteNonNegative(meshingMillis)
                    || !finiteNonNegative(p99FrameMillis)
                    || alertFactor < 1
                    || failFactor < alertFactor) throw new IllegalArgumentException("Invalid performance baseline");
        }
    }

    public record Violation(String metric, double actual, double threshold, Status severity) {}

    public record Evaluation(Status status, boolean matchingHardware, List<Violation> violations) {
        public Evaluation {
            violations = List.copyOf(violations);
        }
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
