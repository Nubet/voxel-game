package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PerformanceGateTest {
    private static final PerformanceGate.Baseline BASELINE =
            new PerformanceGate.Baseline("gpu-a", 5, 10, 33, 1.10, 1.25, true);

    @Test
    void detectsSyntheticRegressionOnControlledHardware() {
        PerformanceGate.Evaluation evaluation =
                PerformanceGate.evaluate(new PerformanceGate.Sample("gpu-a", 7, 10, 50), BASELINE);

        assertEquals(PerformanceGate.Status.FAIL, evaluation.status());
        assertTrue(evaluation.matchingHardware());
        assertTrue(evaluation.violations().stream()
                .anyMatch(violation -> violation.metric().equals("p99FrameMillis")));
    }

    @Test
    void downgradesDifferentHardwareToAlertInsteadOfHardFail() {
        PerformanceGate.Evaluation evaluation =
                PerformanceGate.evaluate(new PerformanceGate.Sample("gpu-b", 7, 10, 50), BASELINE);

        assertEquals(PerformanceGate.Status.ALERT, evaluation.status());
        assertFalse(evaluation.matchingHardware());
        assertTrue(evaluation.violations().stream()
                .allMatch(violation -> violation.severity() == PerformanceGate.Status.ALERT));
    }

    @Test
    void staysQuietInsideAlertThreshold() {
        PerformanceGate.Evaluation evaluation =
                PerformanceGate.evaluate(new PerformanceGate.Sample("gpu-a", 5.4, 10.9, 35.9), BASELINE);

        assertEquals(PerformanceGate.Status.PASS, evaluation.status());
        assertTrue(evaluation.violations().isEmpty());
    }
}
